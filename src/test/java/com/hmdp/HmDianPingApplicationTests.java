package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClinet;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
//    @Test
//    void testSaveShop2Redis() throws InterruptedException {
//        shopService.saveShop2Redis(1L,10L);
//    }
    @Resource
    private CacheClinet cacheClinet;
    private ExecutorService executorService = Executors.newFixedThreadPool(500);
//    @Test
//    void testSaveShop() throws InterruptedException {
//        Shop shop = shopService.getById(1L);
//        cacheClinet.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1l,shop,10L, TimeUnit.SECONDS);
//    }
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
}
