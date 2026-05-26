package com.hmdp.ratelimit.aspect;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.ratelimit.annotation.NoRepeatSubmit;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoRepeatSubmitAspectTest {

    private NoRepeatSubmitAspect aspect;

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> bucket;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private Method method;
    private NoRepeatSubmit annotation;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        aspect = new NoRepeatSubmitAspect(redissonClient);
        method = DemoController.class.getDeclaredMethod("submit", Long.class);
        annotation = method.getAnnotation(NoRepeatSubmit.class);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { 100L });
        when(signature.getMethod()).thenReturn(method);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void around_shouldProceedWhenFirstSubmit() throws Throwable {
        Result expected = Result.ok(1L);
        when(bucket.trySet(anyString(), eq(3L), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(expected);

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isSameAs(expected);
        verify(redissonClient).getBucket("repeat:submit:user:7:seckill:100");
        verify(joinPoint).proceed();
    }

    @Test
    void around_shouldRejectWhenSubmitRepeated() throws Throwable {
        when(signature.getReturnType()).thenReturn(Result.class);
        when(bucket.trySet(anyString(), eq(3L), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(false);

        Object result = aspect.around(joinPoint, annotation);

        assertThat(result).isInstanceOf(Result.class);
        Result response = (Result) result;
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMsg()).isEqualTo("请勿重复点击下单");
        verify(joinPoint, never()).proceed();
    }

    static class DemoController {
        @NoRepeatSubmit(key = "'seckill:' + #voucherId", interval = 3, message = "请勿重复点击下单")
        Result submit(Long voucherId) {
            return Result.ok(voucherId);
        }
    }
}
