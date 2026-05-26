package com.hmdp.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoRepeatSubmit {

    /**
     * SpEL expression for the business key, for example "'seckill:' + #voucherId".
     */
    String key() default "";

    long interval() default 3L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    String message() default "请勿重复提交";
}
