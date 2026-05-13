package com.hmdp.ratelimit.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class BlacklistService {

    private static final String BLACKLIST_KEY = "rate:limit:blacklist:";
    private static final String VIOLATION_KEY = "rate:limit:violation:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean isBlocked(String identifier) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_KEY + identifier));
    }

    public long recordViolation(String identifier, int blacklistThreshold, int blacklistSeconds) {
        String violationKey = VIOLATION_KEY + identifier;
        Long times = stringRedisTemplate.opsForValue().increment(violationKey);
        if (times == null) {
            return 0L;
        }
        if (times == 1L) {
            stringRedisTemplate.expire(violationKey, blacklistSeconds, TimeUnit.SECONDS);
        }
        if (times >= blacklistThreshold) {
            block(identifier, blacklistSeconds);
            stringRedisTemplate.delete(violationKey);
        }
        return times;
    }

    public void block(String identifier, int seconds) {
        stringRedisTemplate.opsForValue().set(BLACKLIST_KEY + identifier, "1", seconds, TimeUnit.SECONDS);
    }
}
