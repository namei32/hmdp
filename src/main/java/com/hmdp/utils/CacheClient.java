package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private static final String LOCK_TOKEN_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final int CACHE_REBUILD_THREADS = 10;
    private static final int CACHE_REBUILD_QUEUE_CAPACITY = 200;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            CACHE_REBUILD_THREADS,
            CACHE_REBUILD_THREADS,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(CACHE_REBUILD_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("cache-rebuild-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time,
                                          TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        R r = dbFallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        set(key, r, time, timeUnit);
        return r;
    }

    public <ID, E> List<E> queryWithPassThroughList(String keyPrefix, ID id, Class<E> elementType,
                                                    Function<ID, List<E>> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotEmpty(json)) {
            return JSONUtil.toList(json, elementType);
        }
        if (json != null) {
            return null;
        }

        List<E> list = dbFallback.apply(id);
        if (list == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        set(key, list, time, timeUnit);
        return list;
    }

    public <R, ID> R queryWithLogicalExpire(ID id, String prefix, Class<R> type, Function<ID, R> dbFallback, Long time,
                                            TimeUnit timeUnit) {
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R cachedValue = JSONUtil.toBean(jsonObject, type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return cachedValue;
        }

        String lockKey = RedisConstants.LOCK_CACHE_REBUILD_KEY + key;
        String lockValue = createLockValue();
        if (tryLock(lockKey, lockValue)) {
            try {
                submitRebuildTask(id, key, lockKey, lockValue, dbFallback, time, timeUnit);
            } catch (RejectedExecutionException ex) {
                log.warn("cache rebuild task rejected, key={}", key, ex);
                unlock(lockKey, lockValue);
            }
        }

        return cachedValue;
    }

    private <R, ID> void submitRebuildTask(ID id, String key, String lockKey, String lockValue,
                                           Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        CACHE_REBUILD_EXECUTOR.execute(() -> {
            long startNanos = System.nanoTime();
            try {
                String latestJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(latestJson)) {
                    RedisData latestRedisData = JSONUtil.toBean(latestJson, RedisData.class);
                    if (latestRedisData.getExpireTime() != null
                            && latestRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                        log.debug("skip cache rebuild because cache is already fresh, key={}", key);
                        return;
                    }
                }

                R latestValue = dbFallback.apply(id);
                if (latestValue == null) {
                    stringRedisTemplate.delete(key);
                    log.warn("cache rebuild returned null, key={}", key);
                    return;
                }

                setWithLogicalExpire(key, latestValue, time, timeUnit);
                log.debug("cache rebuild success, key={}, costMs={}", key,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            } catch (Exception ex) {
                log.error("cache rebuild failed, key={}", key, ex);
            } finally {
                unlock(lockKey, lockValue);
            }
        });
    }

    private boolean tryLock(String key, String value) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                value,
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        );
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key, String value) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }

    private String createLockValue() {
        return LOCK_TOKEN_PREFIX + Thread.currentThread().getId();
    }
}
