package com.innerstyle.auth.security;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}: token round-trip + tamper rejection, and the boot-time
 * fail-fast on an insecure signing secret (finding C2).
 */
class JwtServiceTest {

    private static final String STRONG_SECRET = "unit-test-signing-secret-that-is-long-enough-1234567890";
    private static final String DEFAULT_SECRET =
        "change-me-please-use-a-long-random-secret-at-least-32-bytes";

    private JwtProperties props(String secret) {
        return new JwtProperties(secret, "innerstyle", Duration.ofMinutes(15), Duration.ofDays(7));
    }

    private MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }

    private User user(UUID id, String email, String... roleCodes) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        for (String code : roleCodes) {
            Role role = new Role();
            role.setCode(code);
            u.getRoles().add(role);
        }
        return u;
    }

    @Test
    @DisplayName("generate → parse round-trips subject, roles and email")
    void roundTripCarriesClaims() {
        JwtService jwt = new JwtService(props(STRONG_SECRET), env("prod"));
        UUID id = UUID.randomUUID();

        String token = jwt.generateAccessToken(user(id, "a@b.com", "USER"));
        Claims claims = jwt.parse(token);

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.getIssuer()).isEqualTo("innerstyle");
        assertThat(claims.get("email", String.class)).isEqualTo("a@b.com");
        assertThat(claims.get("roles", List.class)).containsExactly("USER");
    }

    @Test
    @DisplayName("parse rejects a tampered token")
    void parseRejectsTamperedToken() {
        JwtService jwt = new JwtService(props(STRONG_SECRET), env("prod"));
        String token = jwt.generateAccessToken(user(UUID.randomUUID(), "a@b.com", "USER"));
        String tampered = token.substring(0, token.length() - 2)
            + (token.endsWith("a") ? "b" : "a");

        // A corrupted token surfaces as a JwtException (bad signature) or IllegalArgumentException.
        assertThatThrownBy(() -> jwt.parse(tampered))
            .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    @DisplayName("boot fails fast on the default placeholder secret in a non-dev profile (C2)")
    void bootFailsOnDefaultSecretInProd() {
        assertThatThrownBy(() -> new JwtService(props(DEFAULT_SECRET), env("prod")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("boot fails fast on a blank secret in a non-dev profile (C2)")
    void bootFailsOnBlankSecretInProd() {
        assertThatThrownBy(() -> new JwtService(props("   "), env("prod")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("boot tolerates the default secret in the dev profile (warns only)")
    void bootAllowsDefaultSecretInDev() {
        // A dev profile must not throw, so local setup stays frictionless.
        JwtService jwt = new JwtService(props(DEFAULT_SECRET), env("dev"));
        assertThat(jwt).isNotNull();
    }
}
