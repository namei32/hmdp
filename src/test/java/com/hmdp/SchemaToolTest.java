package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class SchemaToolTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void showTableSchema() {
        System.out.println("----- tb_voucher 表结构 -----");
        List<Map<String, Object>> voucherColumns = jdbcTemplate.queryForList("DESCRIBE tb_voucher");
        for (Map<String, Object> col : voucherColumns) {
            System.out.println(col.get("Field") + " - " + col.get("Type"));
        }

        System.out.println("\n----- tb_seckill_voucher 表结构 -----");
        List<Map<String, Object>> seckillColumns = jdbcTemplate.queryForList("DESCRIBE tb_seckill_voucher");
        for (Map<String, Object> col : seckillColumns) {
            System.out.println(col.get("Field") + " - " + col.get("Type"));
        }

        System.out.println("\n----- tb_shop 表结构 -----");
        List<Map<String, Object>> shopColumns = jdbcTemplate.queryForList("DESCRIBE tb_shop");
        for (Map<String, Object> col : shopColumns) {
            System.out.println(col.get("Field") + " - " + col.get("Type"));
        }
    }
}
