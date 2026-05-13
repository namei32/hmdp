package com.hmdp.ratelimit.annotation;

import com.hmdp.ratelimit.enums.RateLimitAlgorithm;
import com.hmdp.ratelimit.enums.RateLimitTarget;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String keyPrefix();

    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.COMPOSITE;

    RateLimitTarget target() default RateLimitTarget.USER_OR_IP;

    int tokensPerRequest() default 1;

    int bucketCapacity() default 20;

    double replenishRate() default 10D;

    int windowSeconds() default 10;

    int windowThreshold() default 20;

    int blacklistThreshold() default 10;

    int blacklistSeconds() default 300;

    String message() default "请求过于频繁，请稍后再试";
}
