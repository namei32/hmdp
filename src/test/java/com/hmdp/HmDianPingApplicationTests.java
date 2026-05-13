package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClinet;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private CacheClinet cacheClinet;
    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    // @Test
    // void testSaveShop() throws InterruptedException {
    // Shop shop = shopService.getById(1L);
    // cacheClinet.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1l,shop,10L,
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
                System.out.println("生成的id为:" + id);
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
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        System.out.println("总耗时:" + (end - begin) + "毫秒");
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
        cacheClinet.setWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY + 1L, // 拼接 Key，例如 cache:shop:1
                shop,
                10L,
                TimeUnit.SECONDS);

        System.out.println("店铺缓存预热成功！");
    }

}
