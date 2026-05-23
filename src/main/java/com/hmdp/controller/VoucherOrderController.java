package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.ratelimit.annotation.RateLimit;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    private final IVoucherOrderService voucherOrderService;

    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @PostMapping("seckill/{id}")
    @RateLimit(
            keyPrefix = "seckill:order",
            bucketCapacity = 40,
            replenishRate = 20D,
            windowSeconds = 5,
            windowThreshold = 10,
            blacklistThreshold = 15,
            blacklistSeconds = 600
    )
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.addOrder(voucherId);
    }
}
