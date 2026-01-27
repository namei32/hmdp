package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1764979200L;
    private  static final  long COUNT_BITS =32;
    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nextId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(java.time.ZoneOffset.UTC);
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;
        long count=stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd")), 1);
        return timestamp << COUNT_BITS | count ; // Placeholder return value

    }


}
