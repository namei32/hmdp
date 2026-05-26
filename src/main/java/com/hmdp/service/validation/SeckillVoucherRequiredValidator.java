package com.hmdp.service.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class SeckillVoucherRequiredValidator implements SeckillVoucherCreateValidator {

    @Override
    public void validate(SeckillVoucherCreateContext context) {
        if (context.getVoucher() == null) {
            throw new RuntimeException("voucher must not be null");
        }
    }
}
