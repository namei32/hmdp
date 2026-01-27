package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
public class CacheClinet {
    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClinet(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
    public  void  setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime( java.time.LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)) );
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function <ID,R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json=stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotEmpty(json)) {
            return JSONUtil.toBean(json, type);
        }
        if(json != null) {
            return null;
        }
        R r=dbFallback.apply(id);
        if(r == null) {
            stringRedisTemplate.opsForValue().set(key, "",RedisConstants.CACHE_NULL_TTL, timeUnit.MINUTES);
            return null;
        }
      this.set(key,r, time, timeUnit);
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(10);
    public <R,ID>R queryWithLogicalExpire(ID id,String prefix,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit timeUnit) {
        String key = prefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(Json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            return  r;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock) {
                try {
                    R r1= dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            };

        return r;
    }
    private  boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private  void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}
