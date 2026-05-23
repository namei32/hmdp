package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1764979200L;
    private static final long COUNT_BITS = 32;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + now.format(DATE_FORMATTER), 1);
        return timestamp << COUNT_BITS | (count == null ? 0 : count);
    }
}
