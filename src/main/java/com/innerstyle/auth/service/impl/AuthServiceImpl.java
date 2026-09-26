package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.LoginAudit;
import com.innerstyle.auth.entity.OauthAccount;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.auth.entity.enums.UserStatus;
import com.innerstyle.auth.mapper.UserMapper;
import com.innerstyle.auth.repository.LoginAuditRepository;
import com.innerstyle.auth.repository.OauthAccountRepository;
import com.innerstyle.auth.repository.RoleRepository;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.auth.security.JwtService;
import com.innerstyle.auth.service.AuthService;
import com.innerstyle.auth.service.RefreshTokenService;
import com.innerstyle.auth.service.social.SocialTokenVerifier;
import com.innerstyle.auth.service.social.SocialUserInfo;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.redis.RedisKeys;
import com.innerstyle.redis.config.RateLimitProperties;
import com.innerstyle.redis.ratelimit.RateLimiterService;
import com.innerstyle.redis.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link AuthService}. Sign-in is Google-only. All flows are i18n-agnostic: errors
 * carry stable message codes resolved by the frontend.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final TokenBlacklist tokenBlacklist;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final RedisKeys redisKeys;
    private final AuthProperties authProperties;
    private final Map<OauthProvider, SocialTokenVerifier> verifiers = new EnumMap<>(OauthProvider.class);

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           OauthAccountRepository oauthAccountRepository,
                           LoginAuditRepository loginAuditRepository,
                           JwtService jwtService, JwtProperties jwtProperties,
                           RefreshTokenService refreshTokenService, UserMapper userMapper,
                           TokenBlacklist tokenBlacklist,
                           RateLimiterService rateLimiterService, RateLimitProperties rateLimitProperties,
                           RedisKeys redisKeys, AuthProperties authProperties,
                           List<SocialTokenVerifier> socialVerifiers) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.loginAuditRepository = loginAuditRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.userMapper = userMapper;
        this.tokenBlacklist = tokenBlacklist;
        this.rateLimiterService = rateLimiterService;
        this.rateLimitProperties = rateLimitProperties;
        this.redisKeys = redisKeys;
        this.authProperties = authProperties;
        socialVerifiers.forEach(v -> this.verifiers.put(v.provider(), v));
    }

    @Override
    @Transactional
    public AuthTokensResponse refresh(String refreshToken, String ip, String userAgent) {
        RefreshTokenService.Issued rotated = refreshTokenService.rotate(refreshToken, ip, userAgent);
        User user = rotated.entity().getUser();
        String access = jwtService.generateAccessToken(user);
        return AuthTokensResponse.of(access, jwtProperties.accessTtl().toSeconds(),
            rotated.rawToken(), userMapper.toProfile(user));
    }

    @Override
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        refreshTokenService.revoke(refreshToken);
        blacklistAccessToken(accessToken);
    }

    /** Revoke the still-valid access token by blacklisting its jti until it would expire. */
    private void blacklistAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtService.parse(accessToken);
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                tokenBlacklist.blacklist(claims.getId(), Duration.ofMillis(ttlMs));
            }
        } catch (RuntimeException ex) {
            log.debug("Could not blacklist access token on logout: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public AuthTokensResponse socialLogin(OauthProvider provider, String providerToken,
                                          String ip, String userAgent) {
        SocialTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            throw new BadRequestException("auth.social.unsupportedProvider");
        }
        SocialUserInfo info = verifier.verify(providerToken);

        User user = oauthAccountRepository
            .findByProviderAndProviderUserId(provider, info.providerUserId())
            .map(OauthAccount::getUser)
            .orElseGet(() -> linkOrCreate(provider, info, ip));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        audit(user.getId(), user.getEmail(), true, "social_" + provider, ip, userAgent);
        return issueTokens(user, ip, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse me(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        return userMapper.toProfile(user);
    }

    @Override
    @Transactional
    public UserProfileResponse acceptPolicy(UUID userId, String version) {
        // Only the version currently served counts: a stale tab must not accept a newer text it never showed.
        if (!authProperties.policyVersion().equals(version)) {
            throw new BadRequestException("auth.policy.versionMismatch");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("user.notFound"));
        user.setAcceptedPolicyVersion(version);
        user.setPolicyAcceptedAt(Instant.now());
        return userMapper.toProfile(user);
    }

    // --------------------------------------------------------------------- helpers

    private User linkOrCreate(OauthProvider provider, SocialUserInfo info, String ip) {
        User user = (info.email() == null ? null
            : userRepository.findByEmailIgnoreCase(info.email()).orElse(null));
        if (user == null) {
            // Every new account starts on the free plan (membership.CreditServiceImpl grants it on
            // first touch) — cap new-account creation per IP so one person can't script up unlimited
            // free credit by farming Google accounts. Fail-closed: a Redis outage blocks new
            // signups rather than silently opening the abuse door.
            RateLimiterService.Decision decision = rateLimiterService.check(
                redisKeys.rateLimit("newAccount", ip), rateLimitProperties.registerPerHour(),
                Duration.ofHours(1).toMillis(), true);
            if (!decision.allowed()) {
                throw new BadRequestException("auth.social.tooManyNewAccounts");
            }
            Role userRole = roleRepository.findByCode(ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("Seed role USER missing"));
            user = new User();
            user.setEmail(info.email() != null ? info.email()
                : provider.name().toLowerCase() + "_" + info.providerUserId() + "@social.local");
            user.setFullName(info.fullName() != null ? info.fullName() : "InnerStyle User");
            user.setAvatarUrl(info.avatarUrl());
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            user.addRole(userRole);
            userRepository.save(user);
        }
        OauthAccount account = new OauthAccount();
        account.setUser(user);
        account.setProvider(provider);
        account.setProviderUserId(info.providerUserId());
        account.setEmail(info.email());
        oauthAccountRepository.save(account);
        return user;
    }

    private AuthTokensResponse issueTokens(User user, String ip, String userAgent) {
        String access = jwtService.generateAccessToken(user);
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user, ip, userAgent);
        return AuthTokensResponse.of(access, jwtProperties.accessTtl().toSeconds(),
            refresh.rawToken(), userMapper.toProfile(user));
    }

    private void audit(UUID userId, String email, boolean success, String reason,
                       String ip, String userAgent) {
        LoginAudit a = new LoginAudit();
        a.setUserId(userId);
        a.setEmailAttempted(email);
        a.setSuccess(success);
        a.setFailureReason(reason);
        a.setIpAddress(ip);
        a.setUserAgent(truncate(userAgent, 255));
        loginAuditRepository.save(a);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
