package com.hmdp.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceImplTest {

    private VoucherServiceImpl voucherService;

    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private ISeckillVoucherService seckillVoucherService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private Cache<Long, List<Voucher>> voucherListLocalCache;

    @BeforeEach
    void setUp() {
        voucherService = org.mockito.Mockito.spy(new VoucherServiceImpl());
        ReflectionTestUtils.setField(voucherService, "baseMapper", voucherMapper);
        ReflectionTestUtils.setField(voucherService, "seckillVoucherService", seckillVoucherService);
        ReflectionTestUtils.setField(voucherService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(voucherService, "voucherListLocalCache", voucherListLocalCache);
    }

    @Test
    void save_shouldEvictVoucherListCacheAfterSuccessfulSave() {
        Voucher voucher = buildVoucher();
        when(voucherMapper.insert(any(Voucher.class))).thenReturn(1);

        boolean saved = voucherService.save(voucher);

        assertThat(saved).isTrue();
        verify(stringRedisTemplate).delete(RedisConstants.CACHE_VOUCHER_LIST_KEY + voucher.getShopId());
        verify(voucherListLocalCache).invalidate(voucher.getShopId());
    }

    @Test
    void addSeckillVoucher_shouldThrowWhenVoucherSaveFails() {
        Voucher voucher = buildVoucher();
        doReturn(false).when(voucherService).save(voucher);

        assertThatThrownBy(() -> voucherService.addSeckillVoucher(voucher))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("voucher save failed");

        verifyNoInteractions(seckillVoucherService);
    }

    @Test
    void addSeckillVoucher_shouldThrowWhenSeckillVoucherSaveFails() {
        Voucher voucher = buildVoucher();
        doReturn(true).when(voucherService).save(voucher);
        when(seckillVoucherService.save(any(SeckillVoucher.class))).thenReturn(false);

        assertThatThrownBy(() -> voucherService.addSeckillVoucher(voucher))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("seckill voucher save failed");
    }

    private Voucher buildVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setShopId(1L);
        voucher.setStock(10);
        voucher.setBeginTime(LocalDateTime.now().plusHours(1));
        voucher.setEndTime(LocalDateTime.now().plusHours(2));
        return voucher;
    }
}
