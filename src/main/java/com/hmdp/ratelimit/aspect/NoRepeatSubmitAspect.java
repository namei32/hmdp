package com.hmdp.ratelimit.aspect;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.ratelimit.annotation.NoRepeatSubmit;
import com.hmdp.ratelimit.util.ClientIpUtils;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
public class NoRepeatSubmitAspect {
    private static final String KEY_PREFIX = "repeat:submit:";

    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public NoRepeatSubmitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(noRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, NoRepeatSubmit noRepeatSubmit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String redisKey = buildRedisKey(joinPoint, method, noRepeatSubmit);

        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        boolean acquired = bucket.trySet(UUID.randomUUID().toString(), noRepeatSubmit.interval(),
                noRepeatSubmit.timeUnit());
        if (!acquired) {
            if (Result.class.isAssignableFrom(signature.getReturnType())) {
                return Result.fail(noRepeatSubmit.message());
            }
            throw new RuntimeException(noRepeatSubmit.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            bucket.delete();
            throw ex;
        }
    }

    private String buildRedisKey(ProceedingJoinPoint joinPoint, Method method, NoRepeatSubmit noRepeatSubmit) {
        String userKey = resolveUserKey();
        String businessKey = resolveBusinessKey(joinPoint, method, noRepeatSubmit.key());
        return KEY_PREFIX + userKey + ":" + businessKey;
    }

    private String resolveBusinessKey(ProceedingJoinPoint joinPoint, Method method, String keyExpression) {
        if (keyExpression == null || keyExpression.isBlank()) {
            return method.getDeclaringClass().getName() + "#" + method.getName();
        }
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, joinPoint.getArgs(),
                parameterNameDiscoverer);
        Object value = parser.parseExpression(keyExpression).getValue(context);
        return value == null ? "null" : value.toString();
    }

    private String resolveUserKey() {
        UserDTO user = UserHolder.getUser();
        if (user != null && user.getId() != null) {
            return "user:" + user.getId();
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            return "ip:" + ClientIpUtils.resolveClientIp(request);
        }
        return "anonymous";
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
