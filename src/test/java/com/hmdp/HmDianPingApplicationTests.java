package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Disabled("Requires local MySQL and Redis; run manually when infrastructure is available.")
@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    // @Test
    // void testSaveShop2Redis() throws InterruptedException {
    // shopService.saveShop2Redis(1L,10L);
    // }
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    private final ExecutorService executorService = Executors.newFixedThreadPool(500);
    // @Test
    // void testSaveShop() throws InterruptedException {
    // Shop shop = shopService.getById(1L);
    // cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L,
    // TimeUnit.SECONDS);
    // }
    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void testIdWorker() {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                log.info("生成的id为: {}", id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 ID 生成任务完成时被中断", e);
        }
        long end = System.currentTimeMillis();
        log.info("总耗时: {} 毫秒", end - begin);
    }

    @Test
    void loadShopData() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> shops = entry.getValue();
            for (Shop shop : shops) {
                stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
            }
        }
    }

    @Test
    void testSaveShop() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY + 1L, // 拼接 Key，例如 cache:shop:1
                shop,
                10L,
                TimeUnit.SECONDS);

        log.info("店铺缓存预热成功");
    }
}
