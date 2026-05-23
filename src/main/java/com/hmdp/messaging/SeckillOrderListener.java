package com.hmdp.messaging;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillOrderConsistencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class SeckillOrderListener {

    private final IVoucherOrderService voucherOrderService;
    private final ISeckillVoucherService seckillVoucherService;
    private final SeckillOrderConsistencyService seckillOrderConsistencyService;

    public SeckillOrderListener(
            IVoucherOrderService voucherOrderService,
            ISeckillVoucherService seckillVoucherService,
            SeckillOrderConsistencyService seckillOrderConsistencyService) {
        this.voucherOrderService = voucherOrderService;
        this.seckillVoucherService = seckillVoucherService;
        this.seckillOrderConsistencyService = seckillOrderConsistencyService;
    }

    @KafkaListener(topics = KafkaTopics.SECKILL_ORDER, groupId = "hmdp-seckill")
    @Transactional
    public void handleSeckillOrder(VoucherOrder voucherOrder) {
        log.info("收到秒杀订单消息: orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Long count = voucherOrderService.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.warn("用户 {} 已经购买过优惠券 {}，跳过", userId, voucherId);
            seckillOrderConsistencyService.markPersisted(voucherOrder.getId());
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.warn("优惠券 {} 数据库库存不足，准备补偿 Redis 预扣库存", voucherId);
            seckillOrderConsistencyService.compensateReservation(
                    voucherId, userId, voucherOrder.getId(), "DB_STOCK_NOT_ENOUGH");
            return;
        }

        try {
            voucherOrderService.save(voucherOrder);
        } catch (DuplicateKeyException e) {
            log.warn("秒杀订单并发重复写入，唯一索引已拦截并交给 Kafka 重试: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherId);
            throw e;
        }
        seckillOrderConsistencyService.markPersistedAfterCommit(voucherOrder);
        log.info("秒杀订单入库成功: orderId={}", voucherOrder.getId());
    }
}
