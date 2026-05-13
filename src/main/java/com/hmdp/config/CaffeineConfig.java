package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.utils.RedisConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Bean("shopLocalCache")
    public Cache<Long, Shop> shopLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(256)
                .maximumSize(RedisConstants.LOCAL_SHOP_CACHE_MAX_SIZE)
                .expireAfterWrite(RedisConstants.LOCAL_SHOP_CACHE_TTL, TimeUnit.MINUTES)
                .build();
    }

    @Bean("voucherListLocalCache")
    public Cache<Long, List<Voucher>> voucherListLocalCache() {
        return Caffeine.newBuilder()
                .initialCapacity(256)
                .maximumSize(RedisConstants.LOCAL_VOUCHER_LIST_CACHE_MAX_SIZE)
                .expireAfterWrite(RedisConstants.LOCAL_VOUCHER_LIST_CACHE_TTL, TimeUnit.MINUTES)
                .build();
    }
}
