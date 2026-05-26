package com.hmdp.service.validation;

import com.hmdp.entity.Voucher;

public class SeckillVoucherCreateContext {
    private final Voucher voucher;

    public SeckillVoucherCreateContext(Voucher voucher) {
        this.voucher = voucher;
    }

    public Voucher getVoucher() {
        return voucher;
    }
}
