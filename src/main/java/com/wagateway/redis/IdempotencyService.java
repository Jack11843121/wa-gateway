package com.wagateway.redis;

import com.wagateway.config.GatewayProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的分布式幂等（SET NX EX）。
 */
@Service
public class IdempotencyService {

    private final StringRedisTemplate redis;
    private final GatewayProperties props;

    public IdempotencyService(StringRedisTemplate redis, GatewayProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * 尝试占据幂等键。首次返回 true，重复返回 false。
     */
    public boolean tryAcquire(String key) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(props.getIdempotent().getTtlSeconds()));
        return Boolean.TRUE.equals(ok);
    }
}