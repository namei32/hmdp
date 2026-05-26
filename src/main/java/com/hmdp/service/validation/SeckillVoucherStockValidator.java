package com.hmdp.service.validation;

import com.hmdp.entity.Voucher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class SeckillVoucherStockValidator implements SeckillVoucherCreateValidator {

    @Override
    public void validate(SeckillVoucherCreateContext context) {
        Voucher voucher = context.getVoucher();
        Integer stock = voucher.getStock();
        if (stock == null || stock <= 0) {
            throw new RuntimeException("invalid seckill voucher arguments");
        }
    }
}
