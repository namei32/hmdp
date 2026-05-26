package com.hmdp.service.validation;

import com.hmdp.entity.Voucher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(30)
public class SeckillVoucherTimeValidator implements SeckillVoucherCreateValidator {

    @Override
    public void validate(SeckillVoucherCreateContext context) {
        Voucher voucher = context.getVoucher();
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if (beginTime == null || endTime == null || endTime.isBefore(beginTime)) {
            throw new RuntimeException("invalid seckill voucher arguments");
        }
    }
}
