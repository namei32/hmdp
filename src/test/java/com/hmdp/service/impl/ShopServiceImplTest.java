package com.hmdp.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheClinet;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    private ShopServiceImpl shopService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CacheClinet cacheClinet;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private Cache<Long, Shop> shopLocalCache;
    @Mock
    private RBloomFilter<Long> bloomFilter;

    @BeforeEach
    void setUp() {
        shopService = org.mockito.Mockito.spy(new ShopServiceImpl());
        ReflectionTestUtils.setField(shopService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(shopService, "cacheClinet", cacheClinet);
        ReflectionTestUtils.setField(shopService, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(shopService, "shopLocalCache", shopLocalCache);

        when(redissonClient.<Long>getBloomFilter(RedisConstants.SHOP_ID_BLOOM_FILTER_KEY))
                .thenReturn(bloomFilter);
    }

    @Test
    void queryShopById_shouldReturnLocalCacheBeforeRedis() {
        Shop shop = new Shop();
        shop.setId(1L);
        when(bloomFilter.contains(1L)).thenReturn(true);
        when(shopLocalCache.getIfPresent(1L)).thenReturn(shop);

        Result result = shopService.queryShopById(1L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(shop);
        verifyNoInteractions(cacheClinet);
    }

    @Test
    void queryShopById_shouldBackfillLocalCacheAfterRedisHit() {
        Shop shop = new Shop();
        shop.setId(1L);
        when(bloomFilter.contains(1L)).thenReturn(true);
        when(shopLocalCache.getIfPresent(1L)).thenReturn(null);
        when(cacheClinet.queryWithPassThrough(
                eq(RedisConstants.CACHE_SHOP_KEY),
                eq(1L),
                eq(Shop.class),
                ArgumentMatchers.<Function<Long, Shop>>any(),
                eq(RedisConstants.CACHE_SHOP_TTL),
                eq(TimeUnit.MINUTES)
        )).thenReturn(shop);

        Result result = shopService.queryShopById(1L);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(shop);
        verify(shopLocalCache).put(1L, shop);
    }

    @Test
    void update_shouldInvalidateRedisAndLocalCache() {
        Shop shop = new Shop();
        shop.setId(1L);
        doReturn(true).when(shopService).updateById(shop);

        Result result = shopService.update(shop);

        assertThat(result.getSuccess()).isTrue();
        verify(shopLocalCache).invalidate(1L);
        verify(stringRedisTemplate).delete(RedisConstants.CACHE_SHOP_KEY + 1L);
    }
}
