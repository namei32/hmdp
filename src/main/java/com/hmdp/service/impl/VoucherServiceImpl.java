package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.validation.SeckillVoucherCreateValidationChain;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Voucher service implementation.
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;
    private final Cache<Long, List<Voucher>> voucherListLocalCache;
    private final SeckillVoucherCreateValidationChain seckillVoucherCreateValidationChain;

    public VoucherServiceImpl(
            ISeckillVoucherService seckillVoucherService,
            StringRedisTemplate stringRedisTemplate,
            CacheClient cacheClient,
            @Qualifier("voucherListLocalCache") Cache<Long, List<Voucher>> voucherListLocalCache,
            SeckillVoucherCreateValidationChain seckillVoucherCreateValidationChain) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
        this.voucherListLocalCache = voucherListLocalCache;
        this.seckillVoucherCreateValidationChain = seckillVoucherCreateValidationChain;
    }

    @Override
    public boolean save(Voucher voucher) {
        boolean saved = super.save(voucher);
        if (saved) {
            handleVoucherListCacheEvict(voucher.getShopId());
        }
        return saved;
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("shopId is invalid");
        }

        List<Voucher> local = voucherListLocalCache.getIfPresent(shopId);
        if (local != null) {
            return Result.ok(local);
        }

        long ttlMinutes = RedisConstants.CACHE_VOUCHER_LIST_TTL
                + ThreadLocalRandom.current().nextLong(0, RedisConstants.CACHE_VOUCHER_LIST_TTL_JITTER + 1);
        List<Voucher> vouchers = cacheClient.queryWithPassThroughList(
                RedisConstants.CACHE_VOUCHER_LIST_KEY,
                shopId,
                Voucher.class,
                baseMapper::queryVoucherOfShop,
                ttlMinutes,
                TimeUnit.MINUTES
        );

        if (vouchers == null) {
            vouchers = Collections.emptyList();
        }
        voucherListLocalCache.put(shopId, vouchers);


        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public boolean addSeckillVoucher(Voucher voucher) {
        seckillVoucherCreateValidationChain.validate(voucher);

        Integer stock = voucher.getStock();
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        boolean voucherSaved = save(voucher);
        if (!voucherSaved) {
            throw new RuntimeException("voucher save failed");
        }

        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(stock);
        seckillVoucher.setBeginTime(beginTime);
        seckillVoucher.setEndTime(endTime);
        boolean seckillSaved = seckillVoucherService.save(seckillVoucher);
        if (!seckillSaved) {
            throw new RuntimeException("seckill voucher save failed");
        }

        // Update cache only after the database transaction commits.
        handleRedisStockWarmup(voucher.getId(), stock);

        return true;
    }

    /**
     * Warm up seckill stock in Redis after commit.
     */
    private void handleRedisStockWarmup(Long voucherId, Integer stock) {
        String key = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String value = stock.toString();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.opsForValue().set(key, value);
                }
            });
        } else {
            stringRedisTemplate.opsForValue().set(key, value);
        }
    }

    private void handleVoucherListCacheEvict(Long shopId) {
        if (shopId == null) {
            return;
        }
        String key = RedisConstants.CACHE_VOUCHER_LIST_KEY + shopId;

        Runnable evict = () -> {
            stringRedisTemplate.delete(key);
            voucherListLocalCache.invalidate(shopId);
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
