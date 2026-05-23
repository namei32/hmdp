package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryOrderTool implements McpTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> STATUS_MAP = ToolUtils.mapOf(
            1, "未支付", 2, "已支付", 3, "已核销", 4, "已取消", 5, "退款中", 6, "已退款"
    );

    private final IVoucherOrderService voucherOrderService;

    public QueryOrderTool(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf(
                "action", ToolUtils.mapOf("type", "string", "description", "操作类型: list_my_orders | order_detail"),
                "orderId", ToolUtils.mapOf("type", "integer", "description", "订单ID (action=order_detail 时必填)"),
                "statusFilter", ToolUtils.mapOf("type", "integer", "description", "按状态筛选: 1=未支付 2=已支付 3=已核销 4=已取消 (action=list_my_orders 时可选)")
        ));
        schema.put("required", ToolUtils.listOf("action"));

        return ToolDefinition.builder()
                .name("query_order")
                .description("查询用户的订单信息。可查询当前用户的订单列表（可按状态筛选），或按订单ID查询订单详情。涉及用户隐私数据，需要用户已登录。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "");

        // require login for order queries
        if (UserHolder.getUser() == null) {
            return ToolResult.fail("查询订单需要登录，请先登录后再查询。");
        }
        Long userId = UserHolder.getUser().getId();

        try {
            switch (action) {
                case "list_my_orders": {
                    Integer statusFilter = toInt(args.get("statusFilter"));
                    List<VoucherOrder> orders;
                    if (statusFilter != null) {
                        orders = voucherOrderService.query()
                                .eq("user_id", userId)
                                .eq("status", statusFilter)
                                .orderByDesc("create_time")
                                .list();
                    } else {
                        orders = voucherOrderService.query()
                                .eq("user_id", userId)
                                .orderByDesc("create_time")
                                .list();
                    }
                    return ToolResult.ok(formatOrderList(orders));
                }
                case "order_detail": {
                    Long orderId = toLong(args.get("orderId"));
                    if (orderId == null) {
                        return ToolResult.fail("orderId 参数不能为空");
                    }
                    VoucherOrder order = voucherOrderService.getById(orderId);
                    if (order == null) {
                        return ToolResult.ok(ToolJsonUtils.empty("order_detail", "未找到该订单"));
                    }
                    if (!order.getUserId().equals(userId)) {
                        return ToolResult.fail("无权查看他人订单");
                    }
                    return ToolResult.ok(formatOrderDetail(order));
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: list_my_orders, order_detail");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询订单失败: " + e.getMessage());
        }
    }

    private String formatOrderList(List<VoucherOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return ToolJsonUtils.empty("order_list", "暂无订单记录");
        }
        List<Map<String, Object>> items = new ArrayList<>(orders.size());
        for (VoucherOrder o : orders) {
            items.add(ToolJsonUtils.object(
                    "id", o.getId(),
                    "voucherId", o.getVoucherId(),
                    "status", o.getStatus(),
                    "statusName", STATUS_MAP.getOrDefault(o.getStatus(), "未知"),
                    "createTime", formatTime(o.getCreateTime())
            ));
        }
        return ToolJsonUtils.list("order_list", orders.size(), items);
    }

    private String formatOrderDetail(VoucherOrder o) {
        Map<String, Object> data = ToolJsonUtils.object(
                "id", o.getId(),
                "voucherId", o.getVoucherId(),
                "userId", o.getUserId(),
                "payType", o.getPayType(),
                "status", o.getStatus(),
                "statusName", STATUS_MAP.getOrDefault(o.getStatus(), "未知"),
                "createTime", formatTime(o.getCreateTime())
        );
        ToolJsonUtils.putIfNotNull(data, "payTime", formatTime(o.getPayTime()));
        ToolJsonUtils.putIfNotNull(data, "useTime", formatTime(o.getUseTime()));
        ToolJsonUtils.putIfNotNull(data, "refundTime", formatTime(o.getRefundTime()));
        return ToolJsonUtils.detail("order_detail", data);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
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

    private Integer toInt(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
