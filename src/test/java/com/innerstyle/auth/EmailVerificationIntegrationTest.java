package com.innerstyle.auth;

import com.innerstyle.auth.entity.EmailVerificationToken;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.entity.enums.UserStatus;
import com.innerstyle.auth.repository.EmailVerificationTokenRepository;
import com.innerstyle.auth.repository.RoleRepository;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.auth.security.TokenHasher;
import com.innerstyle.auth.service.AuthService;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full-context integration tests (real Postgres via Testcontainers). Verifies two things a mock
 * cannot: (1) the Flyway migration actually applied the {@code attempt_count} column and Hibernate
 * validates the schema; (2) a failed OTP attempt truly <b>commits</b> the incremented attempt
 * counter even though {@link AuthService#verifyEmail} throws — i.e. the {@code noRollbackFor}
 * transaction semantics work end-to-end, not just in the object state a unit test observes.
 */
class EmailVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmailVerificationTokenRepository emailTokenRepository;
    @Autowired private TokenHasher tokenHasher;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User seedPendingUser(String email) {
        Role userRole = roleRepository.findByCode("USER").orElseThrow();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("irrelevant-hash");
        user.setFullName("Do Huy");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);
        user.addRole(userRole);
        return userRepository.save(user);
    }

    private UUID seedOtpToken(User user, String otp, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokenHasher.hash(otp));
        token.setExpiresAt(expiresAt);
        token.setAttemptCount(0);
        return emailTokenRepository.save(token).getId();
    }

    @Test
    @DisplayName("Flyway migration added attempt_count and the schema validates")
    void migration_addedAttemptCountColumn() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'dtb_email_verification_tokens' "
                + "AND column_name = 'attempt_count'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("verifyEmail: wrong OTP commits attempt_count++, correct OTP then activates user")
    void verifyEmail_wrongThenRight_commitsAttemptAndActivates() {
        String email = "commit-" + UUID.randomUUID() + "@example.com";
        User user = seedPendingUser(email);
        UUID tokenId = seedOtpToken(user, "123456", Instant.now().plusSeconds(600));

        // Wrong OTP → exception, but the attempt increment must be committed (noRollbackFor).
        assertThatThrownBy(() -> authService.verifyEmail(email, "000000"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.invalid");

        EmailVerificationToken afterWrong = emailTokenRepository.findById(tokenId).orElseThrow();
        assertThat(afterWrong.getAttemptCount()).isEqualTo(1);
        assertThat(afterWrong.getUsedAt()).isNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();

        // Correct OTP → user becomes ACTIVE + verified, token consumed.
        authService.verifyEmail(email, "123456");

        User verified = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(emailTokenRepository.findById(tokenId).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("verifyEmail: attempts exhausted burns the token (persisted used_at)")
    void verifyEmail_exhaustsAttempts_burnsToken() {
        String email = "burn-" + UUID.randomUUID() + "@example.com";
        User user = seedPendingUser(email);
        UUID tokenId = seedOtpToken(user, "654321", Instant.now().plusSeconds(600));

        // otp-max-attempts defaults to 5 → 5 wrong tries increment to the cap.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyEmail(email, "000000"))
                .isInstanceOf(BadRequestException.class);
        }
        assertThat(emailTokenRepository.findById(tokenId).orElseThrow().getAttemptCount())
            .isEqualTo(5);

        // 6th attempt (even with the correct code) is rejected and burns the token.
        assertThatThrownBy(() -> authService.verifyEmail(email, "654321"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("auth.verification.tooManyAttempts");
        assertThat(emailTokenRepository.findById(tokenId).orElseThrow().getUsedAt()).isNotNull();
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }
}
