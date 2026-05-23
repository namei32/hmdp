package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryVoucherTool implements McpTool {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IVoucherService voucherService;
    private final ISeckillVoucherService seckillVoucherService;

    public QueryVoucherTool(IVoucherService voucherService, ISeckillVoucherService seckillVoucherService) {
        this.voucherService = voucherService;
        this.seckillVoucherService = seckillVoucherService;
    }

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
                    if (shopId == null) {
                        return ToolResult.fail("shopId 参数不能为空");
                    }
                    com.hmdp.dto.Result result = voucherService.queryVoucherOfShop(shopId);
                    if (result.getSuccess() && result.getData() != null) {
                        @SuppressWarnings("unchecked")
                        List<Voucher> vouchers = (List<Voucher>) result.getData();
                        return ToolResult.ok(formatVoucherList(vouchers));
                    }
                    return ToolResult.ok(ToolJsonUtils.empty("voucher_list", "该店铺暂无优惠券"));
                }
                case "detail": {
                    Long voucherId = toLong(args.get("voucherId"));
                    if (voucherId == null) {
                        return ToolResult.fail("voucherId 参数不能为空");
                    }
                    Voucher voucher = voucherService.getById(voucherId);
                    if (voucher == null) {
                        return ToolResult.ok(ToolJsonUtils.empty("voucher_detail", "未找到该优惠券"));
                    }
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
        if (vouchers == null || vouchers.isEmpty()) {
            return ToolJsonUtils.empty("voucher_list", "暂无优惠券");
        }
        List<Map<String, Object>> items = new ArrayList<>(vouchers.size());
        for (Voucher v : vouchers) {
            Map<String, Object> item = ToolJsonUtils.object(
                    "id", v.getId(),
                    "shopId", v.getShopId(),
                    "title", v.getTitle(),
                    "subTitle", v.getSubTitle(),
                    "payValue", v.getPayValue() != null ? v.getPayValue() : 0,
                    "actualValue", v.getActualValue() != null ? v.getActualValue() : 0,
                    "type", v.getType(),
                    "typeName", v.getType() != null && v.getType() == 1 ? "秒杀券" : "普通券"
            );
            ToolJsonUtils.putIfNotNull(item, "stock", v.getStock());
            ToolJsonUtils.putIfNotNull(item, "beginTime", formatTime(v.getBeginTime()));
            ToolJsonUtils.putIfNotNull(item, "endTime", formatTime(v.getEndTime()));
            items.add(item);
        }
        return ToolJsonUtils.list("voucher_list", vouchers.size(), items);
    }

    private String formatVoucherDetail(Voucher v, SeckillVoucher seckill) {
        Map<String, Object> data = ToolJsonUtils.object(
                "id", v.getId(),
                "shopId", v.getShopId(),
                "title", v.getTitle(),
                "subTitle", v.getSubTitle(),
                "rules", v.getRules(),
                "payValue", v.getPayValue() != null ? v.getPayValue() : 0,
                "actualValue", v.getActualValue() != null ? v.getActualValue() : 0,
                "type", v.getType(),
                "typeName", v.getType() != null && v.getType() == 1 ? "秒杀券" : "普通券"
        );
        if (seckill != null) {
            data.put("seckill", ToolJsonUtils.object(
                    "stock", seckill.getStock(),
                    "beginTime", formatTime(seckill.getBeginTime()),
                    "endTime", formatTime(seckill.getEndTime())
            ));
        }
        return ToolJsonUtils.detail("voucher_detail", data);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }

    private Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
