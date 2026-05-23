package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private static final String SHOP_NOT_FOUND_MESSAGE = "店铺不存在!";

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    private final RedissonClient redissonClient;
    private final Cache<Long, Shop> shopLocalCache;

    public ShopServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            CacheClient cacheClient,
            RedissonClient redissonClient,
            @Qualifier("shopLocalCache") Cache<Long, Shop> shopLocalCache) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
        this.redissonClient = redissonClient;
        this.shopLocalCache = shopLocalCache;
    }

    @Override
    public Result queryShopById(Long id) {

        RBloomFilter<Long> bloomFilter = getShopBloomFilter();
        if (!bloomFilter.contains(id)) {
            return Result.fail(SHOP_NOT_FOUND_MESSAGE);
        }
        Shop shop = shopLocalCache.getIfPresent(id);
        if (shop != null) {
            log.info("使用Caffeine缓存");
            return Result.ok(shop);
        }
        shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail(SHOP_NOT_FOUND_MESSAGE);
        }
        shopLocalCache.put(id, shop);
        return Result.ok(shop);
    }

    @Override
    public boolean save(Shop shop) {
        boolean saved = super.save(shop);
        if (saved && shop.getId() != null) {
            getShopBloomFilter().add(shop.getId());
            shopLocalCache.invalidate(shop.getId());
        }
        return saved;
    }

    @Override
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        shopLocalCache.invalidate(shopId);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        return Result.ok();
    }

    private RBloomFilter<Long> getShopBloomFilter() {
        return redissonClient.getBloomFilter(RedisConstants.SHOP_ID_BLOOM_FILTER_KEY);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        int from = (current - 1) * pageSize;
        int end = current * pageSize;

        String key = SHOP_GEO_KEY + typeId;

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000), // 5km
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .limit(end));

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content == null || content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        // 1) 取当前页（skip + limit），并收集 shopId + distance
        List<Long> ids = new ArrayList<>(pageSize);
        Map<Long, Double> distanceMap = new HashMap<>(pageSize);

        content.stream()
                .skip(from)
                .limit(pageSize)
                .forEach(geoResult -> {
                    String shopIdStr = geoResult.getContent().getName(); // member
                    Long shopId = Long.valueOf(shopIdStr);
                    ids.add(shopId);

                    Distance distance = geoResult.getDistance();
                    if (distance != null) {
                        distanceMap.put(shopId, distance.getValue()); // 默认单位：米
                    }
                });

        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2) 按 ids 的顺序从 DB 查询（保持与 Redis 的距离排序一致）
        String orderBy = "ORDER BY FIELD(id," + StrUtil.join(",", ids) + ")";
        List<Shop> shops = query()
                .in("id", ids)
                .last(orderBy)
                .list();

        // 3) 回填距离
        for (Shop shop : shops) {
            Double dist = distanceMap.get(shop.getId());
            if (dist != null) {
                shop.setDistance(dist);
            }
        }

        return Result.ok(shops);
    }

}
