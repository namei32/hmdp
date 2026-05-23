package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@Disabled("Requires local MySQL; run manually when database schema inspection is needed.")
@SpringBootTest
public class SchemaToolTest {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void showTableSchema() {
        log.info("----- tb_voucher 表结构 -----");
        List<Map<String, Object>> voucherColumns = jdbcTemplate.queryForList("DESCRIBE tb_voucher");
        for (Map<String, Object> col : voucherColumns) {
            log.info("{} - {}", col.get("Field"), col.get("Type"));
        }

        log.info("----- tb_seckill_voucher 表结构 -----");
        List<Map<String, Object>> seckillColumns = jdbcTemplate.queryForList("DESCRIBE tb_seckill_voucher");
        for (Map<String, Object> col : seckillColumns) {
            log.info("{} - {}", col.get("Field"), col.get("Type"));
        }

        log.info("----- tb_shop 表结构 -----");
        List<Map<String, Object>> shopColumns = jdbcTemplate.queryForList("DESCRIBE tb_shop");
        for (Map<String, Object> col : shopColumns) {
            log.info("{} - {}", col.get("Field"), col.get("Type"));
        }
    }
}
