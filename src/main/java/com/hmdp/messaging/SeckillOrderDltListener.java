package com.hmdp.messaging;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.SeckillOrderConsistencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeckillOrderDltListener {

    private final SeckillOrderConsistencyService seckillOrderConsistencyService;

    public SeckillOrderDltListener(SeckillOrderConsistencyService seckillOrderConsistencyService) {
        this.seckillOrderConsistencyService = seckillOrderConsistencyService;
    }

    @KafkaListener(topics = KafkaTopics.SECKILL_ORDER_DLT, groupId = "hmdp-seckill-dlt")
    public void handleDeadLetter(VoucherOrder voucherOrder) {
        log.error("秒杀订单进入死信队列，准备补偿 Redis 预扣库存: orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
        seckillOrderConsistencyService.compensateReservation(
                voucherOrder.getVoucherId(),
                voucherOrder.getUserId(),
                voucherOrder.getId(),
                "CONSUMER_DLT");
    }
}
