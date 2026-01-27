package com.hmdp.service.impl;

import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.IlockImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static  final DefaultRedisScript<Long>  redisScript;
    static {
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new org.springframework.core.io.ClassPathResource("seckill.lua"));
        redisScript.setResultType(Long.class);
    }
    @Override
    @Transactional
    public Result addOrder(Long voucherId) {
      long result=stringRedisTemplate.execute(
              redisScript,
              Collections.emptyList(),
              voucherId.toString(),
              UserHolder.getUser().getId().toString()
      );
            if(result!=0){
                return Result.fail(result==1?"库存不足":"不能重复下单");
            }
            //TODO 订单入库
            long id = redisIdWorker.nextId("order");
            return Result.ok(id);
    }

//    public Result addOrder(Long voucherId) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(seckillVoucher.getBeginTime())) {
//            return Result.fail("秒杀未开始");
//        }
//        if (now.isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("秒杀已结束");
//        }
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        String name="lock:order:"+userId;
////        IlockImpl ilock = new IlockImpl(stringRedisTemplate,name);
//        RLock ilock= redissonClient.getLock(name);
//        boolean isLock = false;
//        try {
//            isLock = ilock.tryLock(1L, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)  AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
//        finally {
//            ilock.unlock();
//        }
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy = (IVoucherOrderService)  AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        int count = voucherOrderService.query().eq("user_id", userId)
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
        voucherOrderService.save(voucherOrder);
        return Result.ok(orderId);
    }
}
