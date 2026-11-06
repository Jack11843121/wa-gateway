package com.wagateway.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基于 Redis 的固定窗口限流（Lua 保证原子性）。
 */
@Service
public class RateLimiterService {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return current",
            Long.class);

    private final StringRedisTemplate redis;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param key            业务键（如按账号）
     * @param limit          窗口内允许次数
     * @param windowSeconds  窗口秒数
     */
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        Long current = redis.execute(SCRIPT, List.of(key), String.valueOf(windowSeconds));
        return current != null && current <= limit;
    }
}