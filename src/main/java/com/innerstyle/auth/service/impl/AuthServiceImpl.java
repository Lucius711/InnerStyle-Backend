package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.dto.request.LoginRequest;
import com.innerstyle.auth.dto.request.RegisterRequest;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.EmailVerificationToken;
import com.innerstyle.auth.entity.LoginAudit;
import com.innerstyle.auth.entity.OauthAccount;
import com.innerstyle.auth.entity.PasswordResetToken;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.entity.enums.OauthProvider;
import com.innerstyle.auth.entity.enums.UserStatus;
import com.innerstyle.auth.mapper.UserMapper;
import com.innerstyle.auth.repository.EmailVerificationTokenRepository;
import com.innerstyle.auth.repository.LoginAuditRepository;
import com.innerstyle.auth.repository.OauthAccountRepository;
import com.innerstyle.auth.repository.PasswordResetTokenRepository;
import com.innerstyle.auth.repository.RoleRepository;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.auth.security.JwtService;
import com.innerstyle.auth.security.TokenHasher;
import com.innerstyle.auth.service.AuthService;
import com.innerstyle.auth.service.EmailSender;
import com.innerstyle.auth.service.RefreshTokenService;
import com.innerstyle.auth.service.social.SocialTokenVerifier;
import com.innerstyle.auth.service.social.SocialUserInfo;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ConflictException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UnauthorizedException;
import com.innerstyle.redis.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link AuthService}. All flows are i18n-agnostic: errors carry stable message codes
 * (e.g. {@code auth.invalidCredentials}) resolved by the frontend.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository emailTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final TokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final UserMapper userMapper;
    private final AuthProperties authProperties;
    private final TokenBlacklist tokenBlacklist;
    private final Map<OauthProvider, SocialTokenVerifier> verifiers = new EnumMap<>(OauthProvider.class);

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           EmailVerificationTokenRepository emailTokenRepository,
                           PasswordResetTokenRepository resetTokenRepository,
                           OauthAccountRepository oauthAccountRepository,
                           LoginAuditRepository loginAuditRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, JwtProperties jwtProperties,
                           RefreshTokenService refreshTokenService, TokenHasher tokenHasher,
                           EmailSender emailSender, UserMapper userMapper, AuthProperties authProperties,
                           TokenBlacklist tokenBlacklist, List<SocialTokenVerifier> socialVerifiers) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.loginAuditRepository = loginAuditRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.userMapper = userMapper;
        this.authProperties = authProperties;
        this.tokenBlacklist = tokenBlacklist;
        socialVerifiers.forEach(v -> this.verifiers.put(v.provider(), v));
    }

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ConflictException("auth.emailExists");
        }
        Role userRole = roleRepository.findByCode(ROLE_USER)
            .orElseThrow(() -> new IllegalStateException("Seed role USER missing"));

        User user = new User();
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.addRole(userRole);
        userRepository.save(user);

        issueAndSendVerification(user);
        return userMapper.toProfile(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken record = emailTokenRepository.findByTokenHash(tokenHasher.hash(token))
            .orElseThrow(() -> new BadRequestException("auth.verification.invalid"));
        if (record.getUsedAt() != null || record.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("auth.verification.invalid");
        }
        User user = record.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        record.setUsedAt(Instant.now());
        userRepository.save(user);
        emailTokenRepository.save(record);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        // Silent on unknown / already-verified accounts to avoid user enumeration.
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                issueAndSendVerification(user);
            }
        });
    }

    @Override
    @Transactional
    public AuthTokensResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail()).orElse(null);
        if (user == null) {
            audit(null, request.getEmail(), false, "no_such_user", ip, userAgent);
            throw new UnauthorizedException("auth.invalidCredentials");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            audit(user.getId(), request.getEmail(), false, "locked", ip, userAgent);
            throw new UnauthorizedException("auth.accountLocked");
        }
        boolean passwordOk = user.getPasswordHash() != null
            && passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!passwordOk) {
            registerFailedAttempt(user);
            audit(user.getId(), request.getEmail(), false, "bad_password", ip, userAgent);
            throw new UnauthorizedException("auth.invalidCredentials");
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            audit(user.getId(), request.getEmail(), false, "not_verified", ip, userAgent);
            throw new UnauthorizedException("auth.emailNotVerified");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            audit(user.getId(), request.getEmail(), false, "disabled", ip, userAgent);
            throw new UnauthorizedException("auth.accountDisabled");
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        audit(user.getId(), request.getEmail(), true, null, ip, userAgent);
        return issueTokens(user, ip, userAgent);
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
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            resetTokenRepository.invalidateAllForUser(user, Instant.now());
            String raw = tokenHasher.generateToken();
            PasswordResetToken record = new PasswordResetToken();
            record.setUser(user);
            record.setTokenHash(tokenHasher.hash(raw));
            record.setExpiresAt(Instant.now().plus(authProperties.passwordResetTtl()));
            resetTokenRepository.save(record);
            String link = authProperties.frontendBaseUrl() + "/reset-password?token=" + raw;
            emailSender.sendPasswordResetEmail(user.getEmail(), user.getFullName(), link);
        });
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken record = resetTokenRepository.findByTokenHash(tokenHasher.hash(token))
            .orElseThrow(() -> new BadRequestException("auth.reset.invalid"));
        if (record.getUsedAt() != null || record.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("auth.reset.invalid");
        }
        User user = record.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        record.setUsedAt(Instant.now());
        userRepository.save(user);
        resetTokenRepository.save(record);
        // Force re-login on all devices.
        refreshTokenService.revokeAll(user);
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
            .orElseGet(() -> linkOrCreate(provider, info));

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

    // --------------------------------------------------------------------- helpers

    private User linkOrCreate(OauthProvider provider, SocialUserInfo info) {
        User user = (info.email() == null ? null
            : userRepository.findByEmailIgnoreCase(info.email()).orElse(null));
        if (user == null) {
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

    private void issueAndSendVerification(User user) {
        emailTokenRepository.invalidateAllForUser(user, Instant.now());
        String raw = tokenHasher.generateToken();
        EmailVerificationToken record = new EmailVerificationToken();
        record.setUser(user);
        record.setTokenHash(tokenHasher.hash(raw));
        record.setExpiresAt(Instant.now().plus(authProperties.emailVerificationTtl()));
        emailTokenRepository.save(record);
        String link = authProperties.frontendBaseUrl() + "/verify-email?token=" + raw;
        emailSender.sendVerificationEmail(user.getEmail(), user.getFullName(), link);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(attempts);
        if (attempts >= authProperties.maxFailedLogins()) {
            user.setLockedUntil(Instant.now().plus(authProperties.lockDuration()));
            user.setFailedLoginCount(0);
        }
        userRepository.save(user);
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
