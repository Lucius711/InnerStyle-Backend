package com.innerstyle.auth.security;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates short-lived HS256 access tokens. Refresh tokens are opaque and
 * handled separately by {@code RefreshTokenService} (only their hash is persisted).
 */
@Slf4j
@Service
public class JwtService {

    /** Publicly-known placeholder shipped in application.yml; must never sign real tokens. */
    private static final String DEFAULT_SECRET =
        "change-me-please-use-a-long-random-secret-at-least-32-bytes";
    private static final List<String> DEV_PROFILES = List.of("dev", "test", "local");

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props, Environment environment) {
        this.props = props;
        validateSecret(props.secret(), environment);
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Fail fast (finding C2): outside dev/test profiles refuse to start when the JWT secret is
     * blank or still the public placeholder, otherwise anyone could forge tokens with arbitrary
     * roles. In dev/test we only warn so local setup stays frictionless.
     */
    private void validateSecret(String secret, Environment environment) {
        boolean insecure = secret == null || secret.isBlank() || DEFAULT_SECRET.equals(secret.trim());
        if (!insecure) {
            return;
        }
        boolean devProfile = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(p -> DEV_PROFILES.contains(p.toLowerCase()));
        if (devProfile || environment.getActiveProfiles().length == 0) {
            log.warn("JWT secret is blank or the default placeholder. This is INSECURE and only "
                + "tolerated in dev/test. Set a unique random JWT_SECRET (>= 32 bytes) before deploy.");
            return;
        }
        throw new IllegalStateException("app.jwt.secret is unset or the default placeholder. "
            + "Set a unique random JWT_SECRET (>= 32 bytes) for this environment.");
    }

    /** Build a signed access token carrying the user id (subject), email and roles. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTtl());
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        var builder = Jwts.builder()
            .issuer(props.issuer())
            .subject(user.getId().toString())
            .id(UUID.randomUUID().toString())
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp));
        if (user.getEmail() != null) {
            builder.claim("email", user.getEmail());
        }
        return builder.signWith(key).compact();
    }

    /** Parse and verify a token; throws {@link JwtException} if invalid/expired. */
    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .requireIssuer(props.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @SuppressWarnings("unchecked")
    public UserPrincipal toPrincipal(Claims claims) {
        UUID id = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        List<String> roles = claims.get("roles", List.class);
        return UserPrincipal.fromClaims(id, email, roles == null ? List.of() : roles);
    }
}
