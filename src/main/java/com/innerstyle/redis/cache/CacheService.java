package com.innerstyle.redis.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Thin cache-aside helper over the JSON Redis template. Every operation is FAIL-OPEN: if Redis
 * is unavailable the cache simply misses and the caller falls back to the source of truth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> jsonRedisTemplate;

    public Optional<Object> get(String key) {
        try {
            return Optional.ofNullable(jsonRedisTemplate.opsForValue().get(key));
        } catch (RuntimeException ex) {
            log.debug("Cache get failed for {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            jsonRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ex) {
            log.debug("Cache put failed for {}: {}", key, ex.getMessage());
        }
    }

    public void evict(String key) {
        try {
            jsonRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.debug("Cache evict failed for {}: {}", key, ex.getMessage());
        }
    }
}
