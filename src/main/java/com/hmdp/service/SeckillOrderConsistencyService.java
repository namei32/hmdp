package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillOrderConsistencyService {
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_PERSISTED = "PERSISTED";
    private static final String STATUS_FAILED = "FAILED";
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;

    static {
        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(new ClassPathResource("lua/seckill_compensate.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SeckillOrderConsistencyService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String pendingOrderKey(Long orderId) {
        return RedisConstants.SECKILL_PENDING_ORDER_KEY + orderId;
    }

    public void markSent(Long orderId) {
        markStatus(orderId, STATUS_SENT, null);
    }

    public void markPersisted(Long orderId) {
        markStatus(orderId, STATUS_PERSISTED, null);
    }

    public void markPersistedAfterCommit(VoucherOrder voucherOrder) {
        Runnable marker = () -> markPersisted(voucherOrder.getId());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            marker.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    marker.run();
                } catch (RuntimeException e) {
                    log.error("秒杀订单落库成功后更新 Redis pending 状态失败: orderId={}", voucherOrder.getId(), e);
                }
            }
        });
    }

    public void markFailed(Long orderId, String reason) {
        markStatus(orderId, STATUS_FAILED, reason);
    }

    public void compensateReservation(Long voucherId, Long userId, Long orderId, String reason) {
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
        String pendingKey = pendingOrderKey(orderId);

        stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                List.of(stockKey, orderKey, pendingKey),
                userId.toString(),
                reason,
                String.valueOf(TimeUnit.MINUTES.toSeconds(RedisConstants.SECKILL_PENDING_ORDER_TTL)));
        log.warn("秒杀预扣库存已补偿: orderId={}, userId={}, voucherId={}, reason={}",
                orderId, userId, voucherId, reason);
    }

    private void markStatus(Long orderId, String status, String reason) {
        String pendingKey = pendingOrderKey(orderId);
        stringRedisTemplate.opsForHash().put(pendingKey, "status", status);
        if (reason != null) {
            stringRedisTemplate.opsForHash().put(pendingKey, "reason", reason);
        }
        stringRedisTemplate.expire(pendingKey, RedisConstants.SECKILL_PENDING_ORDER_TTL, TimeUnit.MINUTES);
    }
}
