package com.innerstyle.redis.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimiterService}: normal counting plus the per-call fail behaviour
 * (finding M5) — benign buckets fail OPEN, sensitive buckets fail CLOSED, on a Redis error.
 */
class RateLimiterServiceTest {

    private StringRedisTemplate redis;
    private RateLimiterService limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        limiter = new RateLimiterService(redis);
    }

    @Test
    @DisplayName("under the limit → allowed with remaining count")
    void allowsUnderLimit() {
        when(redis.<Long>execute(any(RedisScript.class), anyList(), any())).thenReturn(3L);

        RateLimiterService.Decision d = limiter.check("k", 5, 60_000L);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(2);
    }

    @Test
    @DisplayName("over the limit → denied")
    void deniesOverLimit() {
        when(redis.<Long>execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);

        RateLimiterService.Decision d = limiter.check("k", 5, 60_000L);

        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Redis error + fail-open (default) → request allowed")
    void failOpenOnRedisError() {
        when(redis.<Long>execute(any(RedisScript.class), anyList(), any()))
            .thenThrow(new RuntimeException("redis down"));

        RateLimiterService.Decision d = limiter.check("k", 5, 60_000L);

        assertThat(d.allowed()).isTrue();
    }

    @Test
    @DisplayName("Redis error + fail-closed → request denied (login/payment protection preserved)")
    void failClosedOnRedisError() {
        when(redis.<Long>execute(any(RedisScript.class), anyList(), any()))
            .thenThrow(new RuntimeException("redis down"));

        RateLimiterService.Decision d = limiter.check("k", 5, 60_000L, true);

        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isGreaterThan(0);
    }
}
