package com.innerstyle.redis.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fixed-window rate limiter backed by a single atomic Redis Lua script (INCR + EXPIRE on first
 * hit). FAIL-OPEN: if Redis errors, the request is allowed (availability over strictness).
 */
@Slf4j
@Service
public class RateLimiterService {

    /** Returns the new counter value; sets the window TTL only on the first increment. */
    private static final String LUA = """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /**
     * @param key        the bucket key (already namespaced)
     * @param limit      max requests per window
     * @param windowMs   window length in milliseconds
     * @return decision with remaining count and retry-after seconds
     */
    public Decision check(String key, int limit, long windowMs) {
        long count;
        try {
            Long result = redis.execute(script, List.of(key), String.valueOf(windowMs));
            count = result == null ? 0 : result;
        } catch (RuntimeException ex) {
            log.debug("Rate limiter unavailable ({}), allowing request", ex.getMessage());
            return new Decision(true, limit, 0);
        }
        boolean allowed = count <= limit;
        int remaining = (int) Math.max(0, limit - count);
        long retryAfter = allowed ? 0 : Math.max(1, windowMs / 1000);
        return new Decision(allowed, remaining, retryAfter);
    }

    /** Result of a rate-limit check. */
    public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}
