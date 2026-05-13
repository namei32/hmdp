package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.ISeckillVoucherService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryVoucherTool implements McpTool {

    @Resource
    private IVoucherService voucherService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf(
                "action", ToolUtils.mapOf("type", "string", "description", "操作类型: list_by_shop | detail"),
                "shopId", ToolUtils.mapOf("type", "integer", "description", "店铺ID (action=list_by_shop 时必填)"),
                "voucherId", ToolUtils.mapOf("type", "integer", "description", "优惠券ID (action=detail 时必填)")
        ));
        schema.put("required", ToolUtils.listOf("action"));

        return ToolDefinition.builder()
                .name("query_voucher")
                .description("查询优惠券信息。可按店铺查询优惠券列表（含秒杀券），或按ID查询优惠券详情。返回优惠券标题、规则、价格、库存、有效期等信息。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "");

        try {
            switch (action) {
                case "list_by_shop": {
                    Long shopId = toLong(args.get("shopId"));
                    if (shopId == null) return ToolResult.fail("shopId 参数不能为空");
                    com.hmdp.dto.Result result = voucherService.queryVoucherOfShop(shopId);
                    if (result.getSuccess() && result.getData() != null) {
                        @SuppressWarnings("unchecked")
                        List<Voucher> vouchers = (List<Voucher>) result.getData();
                        return ToolResult.ok(formatVoucherList(vouchers));
                    }
                    return ToolResult.ok("该店铺暂无优惠券");
                }
                case "detail": {
                    Long voucherId = toLong(args.get("voucherId"));
                    if (voucherId == null) return ToolResult.fail("voucherId 参数不能为空");
                    Voucher voucher = voucherService.getById(voucherId);
                    if (voucher == null) return ToolResult.ok("未找到该优惠券");
                    // check if seckill
                    SeckillVoucher seckill = seckillVoucherService.getById(voucherId);
                    return ToolResult.ok(formatVoucherDetail(voucher, seckill));
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: list_by_shop, detail");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询优惠券失败: " + e.getMessage());
        }
    }

    private String formatVoucherList(List<Voucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) return "暂无优惠券";
        StringBuilder sb = new StringBuilder();
        sb.append("该店铺共有 ").append(vouchers.size()).append(" 张优惠券:\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        int idx = 1;
        for (Voucher v : vouchers) {
            sb.append(idx++).append(". ").append(v.getTitle());
            if (v.getSubTitle() != null) sb.append(" (").append(v.getSubTitle()).append(")");
            sb.append(" | 支付:¥").append(v.getPayValue() != null ? v.getPayValue() : 0);
            sb.append(" 抵扣:¥").append(v.getActualValue() != null ? v.getActualValue() : 0);
            if (v.getStock() != null) sb.append(" | 库存:").append(v.getStock());
            if (v.getBeginTime() != null) sb.append(" | 有效期:").append(v.getBeginTime().format(fmt)).append("~").append(v.getEndTime().format(fmt));
            sb.append(" | ID:").append(v.getId());
            sb.append(" | 类型:").append(v.getType() != null && v.getType() == 1 ? "秒杀券" : "普通券");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatVoucherDetail(Voucher v, SeckillVoucher seckill) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        sb.append("优惠券详情:\n");
        sb.append("标题: ").append(v.getTitle()).append("\n");
        if (v.getSubTitle() != null) sb.append("副标题: ").append(v.getSubTitle()).append("\n");
        sb.append("支付金额: ¥").append(v.getPayValue() != null ? v.getPayValue() : 0).append("\n");
        sb.append("抵扣金额: ¥").append(v.getActualValue() != null ? v.getActualValue() : 0).append("\n");
        if (v.getRules() != null) sb.append("使用规则: ").append(v.getRules()).append("\n");
        if (seckill != null) {
            sb.append("类型: 秒杀券\n");
            sb.append("库存: ").append(seckill.getStock()).append("\n");
            sb.append("开始时间: ").append(seckill.getBeginTime() != null ? seckill.getBeginTime().format(fmt) : "无").append("\n");
            sb.append("结束时间: ").append(seckill.getEndTime() != null ? seckill.getEndTime().format(fmt) : "无").append("\n");
        } else {
            sb.append("类型: 普通券\n");
        }
        return sb.toString();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
