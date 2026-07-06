package com.innerstyle.auth.service;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.dto.request.RegisterRequest;
import com.innerstyle.auth.dto.response.UserProfileResponse;
import com.innerstyle.auth.entity.EmailVerificationToken;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
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
import com.innerstyle.auth.service.impl.AuthServiceImpl;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ConflictException;
import com.innerstyle.redis.security.TokenBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the OTP-based email verification flow (register / verifyEmail / resend) and
 * duplicate-registration guard. Uses a real {@link TokenHasher} and real {@link AuthProperties}
 * so the hash-compare and OTP-length behaviour is exercised end-to-end.
 */
class AuthServiceImplTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private EmailVerificationTokenRepository emailTokenRepository;
    private PasswordEncoder passwordEncoder;
    private EmailSender emailSender;
    private UserMapper userMapper;

    private final TokenHasher tokenHasher = new TokenHasher();
    private final AuthProperties authProperties = new AuthProperties(
        "http://localhost:5173", Duration.ofMinutes(15), 5, Duration.ofMinutes(15),
        6, Duration.ofMinutes(10), 5, "no-reply@innerstyle.app", "InnerStyle");

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        emailTokenRepository = mock(EmailVerificationTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailSender = mock(EmailSender.class);
        userMapper = mock(UserMapper.class);

        service = new AuthServiceImpl(
            userRepository, roleRepository, emailTokenRepository,
            mock(PasswordResetTokenRepository.class), mock(OauthAccountRepository.class),
            mock(LoginAuditRepository.class), passwordEncoder, mock(JwtService.class),
            new JwtProperties("0123456789012345678901234567890123", "innerstyle",
                Duration.ofMinutes(15), Duration.ofDays(7)),
            mock(com.innerstyle.auth.service.RefreshTokenService.class), tokenHasher,
            emailSender, userMapper, authProperties, mock(TokenBlacklist.class), List.of());
    }

    // ------------------------------------------------------------------ register

    @Test
    @DisplayName("register: new email → PENDING_VERIFICATION user + numeric OTP emailed (hash stored)")
    void register_new_issuesOtp() {
        RegisterRequest req = new RegisterRequest("huy@example.com", "S3curePass!", "Do Huy");
        when(userRepository.existsByEmailIgnoreCase("huy@example.com")).thenReturn(false);
        when(roleRepository.findByCode("USER")).thenReturn(Optional.of(new Role()));
        when(passwordEncoder.encode("S3curePass!")).thenReturn("BCRYPT_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toProfile(any(User.class))).thenReturn(mock(UserProfileResponse.class));

        service.register(req);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("BCRYPT_HASH");

        ArgumentCaptor<String> otpCap = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationOtp(eq("huy@example.com"), eq("Do Huy"), otpCap.capture());
        assertThat(otpCap.getValue()).matches("\\d{6}");

        ArgumentCaptor<EmailVerificationToken> tokenCap =
            ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailTokenRepository).save(tokenCap.capture());
        // Only the hash of the OTP is persisted.
        assertThat(tokenCap.getValue().getTokenHash())
            .isEqualTo(tokenHasher.hash(otpCap.getValue()));
        assertThat(tokenCap.getValue().getTokenHash()).isNotEqualTo(otpCap.getValue());
    }

    @Test
    @DisplayName("register: duplicate email → ConflictException auth.emailExists")
    void register_duplicate_throwsConflict() {
        when(userRepository.existsByEmailIgnoreCase("huy@example.com")).thenReturn(true);
        RegisterRequest req = new RegisterRequest("huy@example.com", "S3curePass!", "Do Huy");

        assertThatThrownBy(() -> service.register(req))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("auth.emailExists");
        verify(emailSender, never()).sendVerificationOtp(any(), any(), any());
    }

    // ------------------------------------------------------------------ verifyEmail

    private User pendingUser() {
        User u = new User();
        u.setEmail("huy@example.com");
        u.setFullName("Do Huy");
        u.setEmailVerified(false);
        u.setStatus(UserStatus.PENDING_VERIFICATION);
        return u;
    }

    private EmailVerificationToken tokenFor(User u, String otp, Instant expiresAt, int attempts) {
        EmailVerificationToken t = new EmailVerificationToken();
        t.setUser(u);
        t.setTokenHash(tokenHasher.hash(otp));
        t.setExpiresAt(expiresAt);
        t.setAttemptCount(attempts);
        return t;
    }

    @Test
    @DisplayName("verifyEmail: correct OTP → user ACTIVE + verified, token consumed")
    void verifyEmail_correct_activates() {
        User u = pendingUser();
        EmailVerificationToken t = tokenFor(u, "123456", Instant.now().plusSeconds(300), 0);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        when(emailTokenRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(u))
            .thenReturn(Optional.of(t));

        service.verifyEmail("huy@example.com", "123456");

        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(t.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("verifyEmail: wrong OTP → invalid + attempt_count incremented and saved")
    void verifyEmail_wrong_incrementsAttempts() {
        User u = pendingUser();
        EmailVerificationToken t = tokenFor(u, "123456", Instant.now().plusSeconds(300), 0);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        when(emailTokenRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(u))
            .thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.verifyEmail("huy@example.com", "000000"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.invalid");

        assertThat(t.getAttemptCount()).isEqualTo(1);
        assertThat(u.isEmailVerified()).isFalse();
        verify(emailTokenRepository).save(t);
    }

    @Test
    @DisplayName("verifyEmail: expired OTP → auth.verification.expired")
    void verifyEmail_expired() {
        User u = pendingUser();
        EmailVerificationToken t = tokenFor(u, "123456", Instant.now().minusSeconds(1), 0);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        when(emailTokenRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(u))
            .thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.verifyEmail("huy@example.com", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.expired");
    }

    @Test
    @DisplayName("verifyEmail: attempts exhausted → tooManyAttempts + token burned")
    void verifyEmail_tooManyAttempts() {
        User u = pendingUser();
        EmailVerificationToken t = tokenFor(u, "123456", Instant.now().plusSeconds(300), 5);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        when(emailTokenRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(u))
            .thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.verifyEmail("huy@example.com", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.tooManyAttempts");
        assertThat(t.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("verifyEmail: already verified → alreadyVerified")
    void verifyEmail_alreadyVerified() {
        User u = pendingUser();
        u.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.verifyEmail("huy@example.com", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.alreadyVerified");
    }

    @Test
    @DisplayName("verifyEmail: unknown email → invalid (no token lookup)")
    void verifyEmail_unknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("ghost@example.com", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.invalid");
    }

    @Test
    @DisplayName("verifyEmail: no active token → invalid")
    void verifyEmail_noToken() {
        User u = pendingUser();
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        when(emailTokenRepository.findFirstByUserAndUsedAtIsNullOrderByCreatedAtDesc(u))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("huy@example.com", "123456"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.invalid");
    }

    // ------------------------------------------------------------------ resendVerification

    @Test
    @DisplayName("resendVerification: unknown email → silent (no email sent)")
    void resend_unknown_silent() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        service.resendVerification("ghost@example.com");
        verify(emailSender, never()).sendVerificationOtp(any(), any(), any());
    }

    @Test
    @DisplayName("resendVerification: already-verified → no email sent")
    void resend_verified_noEmail() {
        User u = pendingUser();
        u.setEmailVerified(true);
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        service.resendVerification("huy@example.com");
        verify(emailSender, never()).sendVerificationOtp(any(), any(), any());
    }

    @Test
    @DisplayName("resendVerification: unverified → issues + emails a fresh OTP")
    void resend_unverified_sends() {
        User u = pendingUser();
        when(userRepository.findByEmailIgnoreCase("huy@example.com")).thenReturn(Optional.of(u));
        service.resendVerification("huy@example.com");
        verify(emailSender).sendVerificationOtp(eq("huy@example.com"), eq("Do Huy"), any());
    }
}
