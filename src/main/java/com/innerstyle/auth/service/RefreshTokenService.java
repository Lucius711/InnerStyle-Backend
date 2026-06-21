package com.innerstyle.auth.service;

import com.innerstyle.auth.config.JwtProperties;
import com.innerstyle.auth.entity.RefreshToken;
import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.RefreshTokenRepository;
import com.innerstyle.auth.security.TokenHasher;
import com.innerstyle.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Issues, rotates and revokes opaque refresh tokens. The raw token is returned to the caller
 * exactly once (on issue/rotate); only its hash is persisted.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final JwtProperties jwtProperties;

    /** Raw token (give to client) + the persisted grant. */
    public record Issued(String rawToken, RefreshToken entity) {
    }

    @Transactional
    public Issued issue(User user, String ip, String userAgent) {
        String raw = tokenHasher.generateToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(tokenHasher.hash(raw));
        rt.setExpiresAt(Instant.now().plus(jwtProperties.refreshTtl()));
        rt.setIpAddress(ip);
        rt.setUserAgent(userAgent);
        repository.save(rt);
        return new Issued(raw, rt);
    }

    /** Validate + rotate: revoke the presented token and issue a fresh one for the same user. */
    @Transactional
    public Issued rotate(String rawToken, String ip, String userAgent) {
        RefreshToken current = repository.findByTokenHash(tokenHasher.hash(rawToken))
            .orElseThrow(() -> new UnauthorizedException("auth.refresh.invalid"));
        if (!current.isActive(Instant.now())) {
            // Reuse of a revoked/expired token: revoke the whole chain defensively.
            repository.revokeAllForUser(current.getUser(), Instant.now());
            throw new UnauthorizedException("auth.refresh.invalid");
        }
        Issued next = issue(current.getUser(), ip, userAgent);
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(next.entity().getId());
        repository.save(current);
        return next;
    }

    @Transactional
    public User resolveUser(String rawToken) {
        RefreshToken current = repository.findByTokenHash(tokenHasher.hash(rawToken))
            .orElseThrow(() -> new UnauthorizedException("auth.refresh.invalid"));
        return current.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(tokenHasher.hash(rawToken)).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                repository.save(rt);
            }
        });
    }

    @Transactional
    public void revokeAll(User user) {
        repository.revokeAllForUser(user, Instant.now());
    }
}
