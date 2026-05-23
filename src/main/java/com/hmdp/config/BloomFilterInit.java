package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Component
public class BloomFilterInit {
    private final RedissonClient redissonClient;
    private final IShopService shopService;

    public BloomFilterInit(RedissonClient redissonClient, IShopService shopService) {
        this.redissonClient = redissonClient;
        this.shopService = shopService;
    }

    @PostConstruct
    public void initBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstants.SHOP_ID_BLOOM_FILTER_KEY);
        bloomFilter.tryInit(100000, 0.01);
        List<Shop> shops = shopService.query().select("id").list();
        shops.forEach(shop -> bloomFilter.add(shop.getId()));
    }
}
