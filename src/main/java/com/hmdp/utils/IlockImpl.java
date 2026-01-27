package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class IlockImpl implements Ilock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private  static final  String prefix="lock:";
    private  static  final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    public IlockImpl(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        long id= Thread.currentThread().getId();
        String prefixId=ID_PREFIX+id;
        Boolean  success = stringRedisTemplate.opsForValue().setIfAbsent(prefix+name,prefixId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        long id = Thread.currentThread().getId();
        String prefixId = ID_PREFIX + id;
        String value = stringRedisTemplate.opsForValue().get(prefix + name);
        if (value != null && value.equals(prefixId)) {
            stringRedisTemplate.delete(prefixId + name);
        }
    }

}
