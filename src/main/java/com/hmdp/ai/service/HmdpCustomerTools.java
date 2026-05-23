package com.hmdp.ai.service;

import com.hmdp.ai.tool.ToolRegistry;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class HmdpCustomerTools {

    private final ToolRegistry toolRegistry;

    public HmdpCustomerTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Tool(name = "query_shop", description = "查询商户信息。支持按ID精确查询、按名称模糊搜索、按类型分页查询、按位置搜索附近商户。返回商户名称、评分、均价、地址、营业时间等信息。")
    public String queryShop(
            @ToolParam(description = "操作类型: query_by_id | query_by_name | query_by_type | query_nearby") String action,
            @ToolParam(description = "商户ID，action=query_by_id 时使用", required = false) Long shopId,
            @ToolParam(description = "商户名称关键字，action=query_by_name 时使用", required = false) String name,
            @ToolParam(description = "商户类型ID，action=query_by_type 或 query_nearby 时使用", required = false) Integer typeId,
            @ToolParam(description = "用户经度，action=query_nearby 时使用", required = false) Double x,
            @ToolParam(description = "用户纬度，action=query_nearby 时使用", required = false) Double y,
            @ToolParam(description = "页码，默认1", required = false) Integer current,
            @ToolParam(description = "每页条数，默认5", required = false) Integer pageSize) {
        Map<String, Object> args = args("action", action);
        putIfPresent(args, "shopId", shopId);
        putIfPresent(args, "name", name);
        putIfPresent(args, "typeId", typeId);
        putIfPresent(args, "x", x);
        putIfPresent(args, "y", y);
        putIfPresent(args, "current", current);
        putIfPresent(args, "pageSize", pageSize);
        return execute("query_shop", args);
    }

    @Tool(name = "query_voucher", description = "查询优惠券信息。可按店铺查询优惠券列表，或按ID查询优惠券详情，包含秒杀券信息、库存、有效期等。")
    public String queryVoucher(
            @ToolParam(description = "操作类型: list_by_shop | detail") String action,
            @ToolParam(description = "店铺ID，action=list_by_shop 时使用", required = false) Long shopId,
            @ToolParam(description = "优惠券ID，action=detail 时使用", required = false) Long voucherId) {
        Map<String, Object> args = args("action", action);
        putIfPresent(args, "shopId", shopId);
        putIfPresent(args, "voucherId", voucherId);
        return execute("query_voucher", args);
    }

    @Tool(name = "query_order", description = "查询当前登录用户的订单信息。可查询订单列表或订单详情。涉及隐私数据，未登录时会提示用户先登录。")
    public String queryOrder(
            @ToolParam(description = "操作类型: list_my_orders | order_detail") String action,
            @ToolParam(description = "订单ID，action=order_detail 时使用", required = false) Long orderId,
            @ToolParam(description = "订单状态筛选: 1=未支付 2=已支付 3=已核销 4=已取消", required = false) Integer statusFilter) {
        Map<String, Object> args = args("action", action);
        putIfPresent(args, "orderId", orderId);
        putIfPresent(args, "statusFilter", statusFilter);
        return execute("query_order", args);
    }

    @Tool(name = "query_blog", description = "查询探店笔记。可按热度排行查询、按ID查看笔记详情、或按用户ID查询其发表的笔记。")
    public String queryBlog(
            @ToolParam(description = "操作类型: hot_blogs | blog_detail | user_blogs") String action,
            @ToolParam(description = "页码，action=hot_blogs 时使用，默认1", required = false) Integer current,
            @ToolParam(description = "笔记ID，action=blog_detail 时使用", required = false) Long blogId,
            @ToolParam(description = "用户ID，action=user_blogs 时使用", required = false) Long userId) {
        Map<String, Object> args = args("action", action);
        putIfPresent(args, "current", current);
        putIfPresent(args, "blogId", blogId);
        putIfPresent(args, "userId", userId);
        return execute("query_blog", args);
    }

    @Tool(name = "query_user", description = "查询用户信息。可查询当前登录用户的信息，或按用户ID查询其他用户基本信息。")
    public String queryUser(
            @ToolParam(description = "操作类型: get_my_info | get_user_info") String action,
            @ToolParam(description = "用户ID，action=get_user_info 时使用", required = false) Long userId) {
        Map<String, Object> args = args("action", action);
        putIfPresent(args, "userId", userId);
        return execute("query_user", args);
    }

    @Tool(name = "get_help", description = "获取客服支持能力列表。当用户询问你能做什么、有什么功能、如何使用平台时调用。")
    public String getHelp() {
        return execute("get_help", new HashMap<>());
    }

    private String execute(String toolName, Map<String, Object> args) {
        ToolResult result = toolRegistry.get(toolName).execute(args);
        if (result.isSuccess()) {
            return result.getContent();
        }
        return ToolJsonUtils.error(toolName, result.getError());
    }

    private Map<String, Object> args(String key, Object value) {
        Map<String, Object> args = new HashMap<>();
        putIfPresent(args, key, value);
        return args;
    }

    private void putIfPresent(Map<String, Object> args, String key, Object value) {
        if (value != null) {
            args.put(key, value);
        }
    }
}
