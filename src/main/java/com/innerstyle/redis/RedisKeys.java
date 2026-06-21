package com.innerstyle.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds namespaced Redis keys: {@code <prefix>:<domain>:<...>}.
 */
@Component
public class RedisKeys {

    private final String prefix;

    public RedisKeys(@Value("${app.redis.key-prefix:is:dev}") String prefix) {
        this.prefix = prefix;
    }

    public String cache(String suffix) {
        return prefix + ":cache:" + suffix;
    }

    public String rateLimit(String bucket, String id) {
        return prefix + ":rl:" + bucket + ":" + id;
    }

    public String blacklist(String jti) {
        return prefix + ":jwt:bl:" + jti;
    }
}
