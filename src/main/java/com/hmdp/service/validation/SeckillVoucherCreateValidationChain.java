package com.hmdp.service.validation;

import com.hmdp.entity.Voucher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeckillVoucherCreateValidationChain {
    private final List<SeckillVoucherCreateValidator> validators;

    public SeckillVoucherCreateValidationChain(List<SeckillVoucherCreateValidator> validators) {
        this.validators = validators;
    }

    public void validate(Voucher voucher) {
        SeckillVoucherCreateContext context = new SeckillVoucherCreateContext(voucher);
        for (SeckillVoucherCreateValidator validator : validators) {
            validator.validate(context);
        }
    }
}
