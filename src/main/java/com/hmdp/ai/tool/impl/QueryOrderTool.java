package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class QueryOrderTool implements McpTool {

    @Resource
    private IVoucherOrderService voucherOrderService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<Integer, String> STATUS_MAP = ToolUtils.mapOf(
            1, "未支付", 2, "已支付", 3, "已核销", 4, "已取消", 5, "退款中", 6, "已退款"
    );

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
                    if (orderId == null) return ToolResult.fail("orderId 参数不能为空");
                    VoucherOrder order = voucherOrderService.getById(orderId);
                    if (order == null) return ToolResult.ok("未找到该订单");
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
        if (orders == null || orders.isEmpty()) return "暂无订单记录";
        StringBuilder sb = new StringBuilder();
        sb.append("您的订单 (共 ").append(orders.size()).append(" 条):\n");
        int idx = 1;
        for (VoucherOrder o : orders) {
            sb.append(idx++).append(". 订单ID:").append(o.getId())
                    .append(" | 优惠券ID:").append(o.getVoucherId())
                    .append(" | 状态:").append(STATUS_MAP.getOrDefault(o.getStatus(), "未知"))
                    .append(" | 下单时间:").append(o.getCreateTime() != null ? o.getCreateTime().format(FMT) : "无")
                    .append("\n");
        }
        return sb.toString();
    }

    private String formatOrderDetail(VoucherOrder o) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单详情:\n");
        sb.append("订单ID: ").append(o.getId()).append("\n");
        sb.append("优惠券ID: ").append(o.getVoucherId()).append("\n");
        sb.append("用户ID: ").append(o.getUserId()).append("\n");
        sb.append("支付方式: ").append(o.getPayType() != null ? o.getPayType() : "无").append("\n");
        sb.append("状态: ").append(STATUS_MAP.getOrDefault(o.getStatus(), "未知")).append("\n");
        sb.append("下单时间: ").append(o.getCreateTime() != null ? o.getCreateTime().format(FMT) : "无").append("\n");
        if (o.getPayTime() != null) sb.append("支付时间: ").append(o.getPayTime().format(FMT)).append("\n");
        if (o.getUseTime() != null) sb.append("核销时间: ").append(o.getUseTime().format(FMT)).append("\n");
        if (o.getRefundTime() != null) sb.append("退款时间: ").append(o.getRefundTime().format(FMT)).append("\n");
        return sb.toString();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
