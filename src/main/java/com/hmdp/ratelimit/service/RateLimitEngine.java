package com.hmdp.ratelimit.service;

import com.hmdp.ratelimit.annotation.RateLimit;
import com.hmdp.ratelimit.enums.RateLimitAlgorithm;
import com.hmdp.ratelimit.model.RateLimitDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
public class RateLimitEngine {

    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final StringRedisTemplate stringRedisTemplate;
    private final BlacklistService blacklistService;

    public RateLimitEngine(
            @Qualifier("tokenBucketScript") DefaultRedisScript<Long> tokenBucketScript,
            @Qualifier("slidingWindowScript") DefaultRedisScript<Long> slidingWindowScript,
            StringRedisTemplate stringRedisTemplate,
            BlacklistService blacklistService) {
        this.tokenBucketScript = tokenBucketScript;
        this.slidingWindowScript = slidingWindowScript;
        this.stringRedisTemplate = stringRedisTemplate;
        this.blacklistService = blacklistService;
    }

    public RateLimitDecision check(String identifier, RateLimit rateLimit) {
        if (blacklistService.isBlocked(identifier)) {
            return new RateLimitDecision(false, true, "访问已被临时封禁，请稍后再试");
        }

        boolean allowed = true;
        RateLimitAlgorithm algorithm = rateLimit.algorithm();
        if (algorithm == RateLimitAlgorithm.TOKEN_BUCKET || algorithm == RateLimitAlgorithm.COMPOSITE) {
            allowed = allowByTokenBucket(identifier, rateLimit);
        }
        if (allowed && (algorithm == RateLimitAlgorithm.SLIDING_WINDOW || algorithm == RateLimitAlgorithm.COMPOSITE)) {
            allowed = allowBySlidingWindow(identifier, rateLimit);
        }

        if (allowed) {
            return new RateLimitDecision(true, false, null);
        }

        long violationTimes = blacklistService.recordViolation(identifier,
                rateLimit.blacklistThreshold(),
                rateLimit.blacklistSeconds());
        log.warn("Rate limit rejected, identifier={}, keyPrefix={}, violations={}",
                identifier, rateLimit.keyPrefix(), violationTimes);
        return new RateLimitDecision(false, false, rateLimit.message());
    }

    private boolean allowByTokenBucket(String identifier, RateLimit rateLimit) {
        String key = buildKey("tb", rateLimit.keyPrefix(), identifier);
        Long result = stringRedisTemplate.execute(tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(rateLimit.bucketCapacity()),
                String.valueOf(rateLimit.replenishRate()),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(rateLimit.tokensPerRequest()));
        return result != null && result == 1L;
    }

    private boolean allowBySlidingWindow(String identifier, RateLimit rateLimit) {
        String key = buildKey("sw", rateLimit.keyPrefix(), identifier);
        String member = System.currentTimeMillis() + "-" + UUID.randomUUID().toString();
        Long result = stringRedisTemplate.execute(slidingWindowScript,
                Collections.singletonList(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(rateLimit.windowSeconds() * 1000L),
                String.valueOf(rateLimit.windowThreshold()),
                member);
        return result != null && result == 1L;
    }

    private String buildKey(String algorithmPrefix, String keyPrefix, String identifier) {
        return "rate:limit:" + algorithmPrefix + ":" + keyPrefix + ":" + identifier;
    }
}
