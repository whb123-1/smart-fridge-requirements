package com.xianzhi.fridge.shared.application;

import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimiter {
    private final StringRedisTemplate redis;
    public RateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public boolean exceeded(String bucket, int limit, Duration duration) {
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count != null && count == 1) redis.expire(bucket, duration);
            return count != null && count > limit;
        } catch (DataAccessException unavailable) {
            return false;
        }
    }
}
