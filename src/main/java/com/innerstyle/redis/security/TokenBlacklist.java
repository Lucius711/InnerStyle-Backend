package com.innerstyle.redis.security;

import com.innerstyle.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed access-token blacklist (revoke-before-expiry on logout). FAIL-OPEN: if Redis is
 * down, tokens are treated as not-blacklisted (they still expire by their short TTL).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklist {

    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    public void blacklist(String jti, Duration ttl) {
        if (jti == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(keys.blacklist(jti), "1", ttl);
        } catch (RuntimeException ex) {
            log.debug("Blacklist write failed for jti {}: {}", jti, ex.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(keys.blacklist(jti)));
        } catch (RuntimeException ex) {
            log.debug("Blacklist check failed for jti {}: {}", jti, ex.getMessage());
            return false;
        }
    }
}
