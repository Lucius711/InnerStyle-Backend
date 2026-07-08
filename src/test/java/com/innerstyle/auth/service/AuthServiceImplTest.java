package com.innerstyle.auth.service;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.dto.response.AuthTokensResponse;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.OauthAccount;
import com.innerstyle.auth.entity.RefreshToken;
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
import com.innerstyle.auth.service.impl.AuthServiceImpl;
import com.innerstyle.auth.service.social.SocialTokenVerifier;
import com.innerstyle.auth.service.social.SocialUserInfo;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.redis.security.TokenBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}. Sign-in is social-only (Google / Facebook); the suite
 * covers social login (existing link, brand-new user, link-by-email, unsupported provider),
 * token refresh, logout and profile lookup.
 */
class AuthServiceImplTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private OauthAccountRepository oauthAccountRepository;
    private LoginAuditRepository loginAuditRepository;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private UserMapper userMapper;
    private TokenBlacklist tokenBlacklist;
    private SocialTokenVerifier googleVerifier;
    private AuthServiceImpl service;

    // A real record (avoids mocking a final type); values are irrelevant beyond accessTtl.
    private final JwtProperties jwtProperties = new JwtProperties(
        "test-secret-key-that-is-at-least-32-bytes-long!!", "innerstyle",
        Duration.ofMinutes(15), Duration.ofDays(7));

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        oauthAccountRepository = mock(OauthAccountRepository.class);
        loginAuditRepository = mock(LoginAuditRepository.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        userMapper = mock(UserMapper.class);
        tokenBlacklist = mock(TokenBlacklist.class);

        googleVerifier = mock(SocialTokenVerifier.class);
        when(googleVerifier.provider()).thenReturn(OauthProvider.GOOGLE);

        service = new AuthServiceImpl(userRepository, roleRepository, oauthAccountRepository,
            loginAuditRepository, jwtService, jwtProperties, refreshTokenService, userMapper,
            tokenBlacklist, List.of(googleVerifier));
    }

    // ------------------------------------------------------------------ socialLogin

    @Test
    @DisplayName("socialLogin: existing linked account → issues tokens for that user")
    void socialLogin_existingAccount_issuesTokens() {
        User user = buildUser(UUID.randomUUID(), "huy@example.com");
        OauthAccount account = new OauthAccount();
        account.setUser(user);
        SocialUserInfo info = new SocialUserInfo(OauthProvider.GOOGLE, "g-123",
            "huy@example.com", "Huy", null);

        when(googleVerifier.verify("token")).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(OauthProvider.GOOGLE, "g-123"))
            .thenReturn(Optional.of(account));
        when(jwtService.generateAccessToken(user)).thenReturn("access-jwt");
        when(refreshTokenService.issue(eq(user), any(), any()))
            .thenReturn(new RefreshTokenService.Issued("refresh-raw", refreshTokenFor(user)));
        UserProfileResponse profile = profileOf(user);
        when(userMapper.toProfile(user)).thenReturn(profile);

        AuthTokensResponse res = service.socialLogin(OauthProvider.GOOGLE, "token", "1.2.3.4", "UA");

        assertThat(res.accessToken()).isEqualTo("access-jwt");
        assertThat(res.refreshToken()).isEqualTo("refresh-raw");
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(res.user()).isEqualTo(profile);
        verify(userRepository).save(user);            // lastLoginAt updated
        verify(loginAuditRepository).save(any());      // audited
        verify(oauthAccountRepository, never()).save(any()); // nothing to link
    }

    @Test
    @DisplayName("socialLogin: no account + unknown email → creates ACTIVE verified user + links oauth")
    void socialLogin_newUser_createsAndLinks() {
        Role role = new Role();
        role.setCode("USER");
        SocialUserInfo info = new SocialUserInfo(OauthProvider.GOOGLE, "g-999",
            "new@example.com", "New User", "http://cdn/x.png");

        when(googleVerifier.verify("token")).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(OauthProvider.GOOGLE, "g-999"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("USER")).thenReturn(Optional.of(role));
        when(jwtService.generateAccessToken(any())).thenReturn("access-jwt");
        when(refreshTokenService.issue(any(), any(), any()))
            .thenReturn(new RefreshTokenService.Issued("refresh-raw", refreshTokenFor(null)));
        when(userMapper.toProfile(any())).thenReturn(profileOf(null));

        service.socialLogin(OauthProvider.GOOGLE, "token", "1.2.3.4", "UA");

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        User created = userCap.getValue();
        assertThat(created.getEmail()).isEqualTo("new@example.com");
        assertThat(created.getFullName()).isEqualTo("New User");
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.isEmailVerified()).isTrue();

        ArgumentCaptor<OauthAccount> accCap = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository).save(accCap.capture());
        assertThat(accCap.getValue().getProvider()).isEqualTo(OauthProvider.GOOGLE);
        assertThat(accCap.getValue().getProviderUserId()).isEqualTo("g-999");
    }

    @Test
    @DisplayName("socialLogin: email matches an existing user → links oauth, does not create a user")
    void socialLogin_existingEmail_linksToExistingUser() {
        User existing = buildUser(UUID.randomUUID(), "huy@example.com");
        SocialUserInfo info = new SocialUserInfo(OauthProvider.GOOGLE, "g-555",
            "huy@example.com", "Huy", null);

        when(googleVerifier.verify("token")).thenReturn(info);
        when(oauthAccountRepository.findByProviderAndProviderUserId(OauthProvider.GOOGLE, "g-555"))
            .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(existing)).thenReturn("access-jwt");
        when(refreshTokenService.issue(eq(existing), any(), any()))
            .thenReturn(new RefreshTokenService.Issued("refresh-raw", refreshTokenFor(existing)));
        when(userMapper.toProfile(existing)).thenReturn(profileOf(existing));

        service.socialLogin(OauthProvider.GOOGLE, "token", "1.2.3.4", "UA");

        verify(oauthAccountRepository).save(any());        // linked the new provider identity
        verify(roleRepository, never()).findByCode(any()); // no brand-new user was created
    }

    @Test
    @DisplayName("socialLogin: provider without a registered verifier → BadRequestException")
    void socialLogin_unsupportedProvider_throws() {
        // Only a GOOGLE verifier is registered in setUp().
        assertThatThrownBy(() -> service.socialLogin(OauthProvider.FACEBOOK, "token", "ip", "ua"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.social.unsupportedProvider");
    }

    // ------------------------------------------------------------------ refresh / logout

    @Test
    @DisplayName("refresh: rotates the refresh token and returns a fresh access token")
    void refresh_rotatesAndIssuesAccess() {
        User user = buildUser(UUID.randomUUID(), "huy@example.com");
        RefreshToken rotated = refreshTokenFor(user);
        when(refreshTokenService.rotate("old-raw", "ip", "ua"))
            .thenReturn(new RefreshTokenService.Issued("new-raw", rotated));
        when(jwtService.generateAccessToken(user)).thenReturn("access-jwt");
        when(userMapper.toProfile(user)).thenReturn(profileOf(user));

        AuthTokensResponse res = service.refresh("old-raw", "ip", "ua");

        assertThat(res.accessToken()).isEqualTo("access-jwt");
        assertThat(res.refreshToken()).isEqualTo("new-raw");
    }

    @Test
    @DisplayName("logout: revokes the refresh token (null access token is a no-op)")
    void logout_revokesRefresh_nullAccessNoop() {
        service.logout("refresh-raw", null);

        verify(refreshTokenService).revoke("refresh-raw");
        verifyNoInteractions(tokenBlacklist);
    }

    // ------------------------------------------------------------------ me

    @Test
    @DisplayName("me: existing id → returns the mapped profile")
    void me_found_returnsProfile() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "huy@example.com");
        UserProfileResponse profile = profileOf(user);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toProfile(user)).thenReturn(profile);

        assertThat(service.me(id)).isEqualTo(profile);
    }

    @Test
    @DisplayName("me: unknown id → ResourceNotFoundException")
    void me_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(id))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("user.notFound");
    }

    // ------------------------------------------------------------------ helpers

    private User buildUser(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Test User");
        u.setStatus(UserStatus.ACTIVE);
        Role role = new Role();
        role.setCode("USER");
        u.addRole(role);
        return u;
    }

    private UserProfileResponse profileOf(User u) {
        return new UserProfileResponse(
            u == null ? UUID.randomUUID() : u.getId(),
            u == null ? "x@example.com" : u.getEmail(),
            "Test User", null, "ACTIVE", true, List.of("USER"), Instant.now());
    }

    private RefreshToken refreshTokenFor(User u) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(u);
        return rt;
    }
}
