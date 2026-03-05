package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static com.hmdp.config.RabbitMQConfig.SECKILL_ORDER_QUEUE;

@Slf4j
@Component
public class SeckillOrderListener {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 监听秒杀订单队列，异步完成数据库下单
     */
    @RabbitListener(queues = SECKILL_ORDER_QUEUE)
    @Transactional
    public void handleSeckillOrder(VoucherOrder voucherOrder) {
        log.info("收到秒杀订单消息: orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());

        // 1. 一人一单校验（兜底，Redis 层已经判断过了）
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = voucherOrderService.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.warn("用户 {} 已经购买过优惠券 {}，跳过", userId, voucherId);
            return;
        }

        // 2. 扣减数据库库存（乐观锁：stock > 0）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.warn("优惠券 {} 库存不足，扣减失败", voucherId);
            return;
        }

        // 3. 写入订单
        voucherOrderService.save(voucherOrder);
        log.info("秒杀订单入库成功: orderId={}", voucherOrder.getId());
    }
}
