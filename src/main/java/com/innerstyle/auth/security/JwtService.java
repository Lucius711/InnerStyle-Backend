package com.innerstyle.auth.security;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.entity.Role;
import com.innerstyle.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Build a signed access token carrying the user id (subject), email and roles. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTtl());
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        return Jwts.builder()
            .issuer(props.issuer())
            .subject(user.getId().toString())
            .id(UUID.randomUUID().toString())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact();
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
