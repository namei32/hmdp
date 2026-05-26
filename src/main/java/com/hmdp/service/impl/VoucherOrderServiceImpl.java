package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.messaging.KafkaTopics;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.MessageOutboxService;
import com.hmdp.service.SeckillOrderConsistencyService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageOutboxService messageOutboxService;
    private final SeckillOrderConsistencyService seckillOrderConsistencyService;

    public VoucherOrderServiceImpl(
            ISeckillVoucherService seckillVoucherService,
            RedisIdWorker redisIdWorker,
            StringRedisTemplate stringRedisTemplate,
            MessageOutboxService messageOutboxService,
            SeckillOrderConsistencyService seckillOrderConsistencyService) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageOutboxService = messageOutboxService;
        this.seckillOrderConsistencyService = seckillOrderConsistencyService;
    }

    @Override
    public Result addOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
        String pendingKey = seckillOrderConsistencyService.pendingOrderKey(orderId);
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(stockKey, orderKey, pendingKey),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId),
                String.valueOf(TimeUnit.MINUTES.toSeconds(RedisConstants.SECKILL_PENDING_ORDER_TTL)));
        if (result == null) {
            return Result.fail("秒杀请求处理失败");
        }
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        try {
            messageOutboxService.enqueue(KafkaTopics.SECKILL_ORDER, String.valueOf(orderId), voucherOrder);
            log.info("秒杀订单消息已写入 outbox: orderId={}", orderId);
        } catch (RuntimeException e) {
            log.error("秒杀订单消息写入 outbox 失败，准备补偿 Redis 预扣库存: orderId={}", orderId, e);
            compensateAfterOutboxFailure(voucherId, userId, orderId);
            return Result.fail("秒杀排队失败，请稍后重试");
        }

        return Result.ok(orderId);
    }

    private void compensateAfterOutboxFailure(Long voucherId, Long userId, Long orderId) {
        try {
            seckillOrderConsistencyService.compensateReservation(voucherId, userId, orderId, "OUTBOX_SAVE_FAILED");
        } catch (RuntimeException compensationFailure) {
            log.error("秒杀订单消息写入 outbox 失败后的 Redis 补偿也失败: orderId={}", orderId, compensationFailure);
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已购买");
        }
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
