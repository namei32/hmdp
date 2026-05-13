package com.hmdp.ai.tool.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryShopTool implements McpTool {

    @Resource
    private IShopService shopService;

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf(
                "action", ToolUtils.mapOf("type", "string", "description", "操作类型: query_by_id | query_by_name | query_by_type | query_nearby"),
                "shopId", ToolUtils.mapOf("type", "integer", "description", "商户ID (action=query_by_id 时必填)"),
                "name", ToolUtils.mapOf("type", "string", "description", "商户名称关键字 (action=query_by_name 时必填)"),
                "typeId", ToolUtils.mapOf("type", "integer", "description", "商户类型ID (action=query_by_type 时必填)"),
                "x", ToolUtils.mapOf("type", "number", "description", "用户经度 (action=query_nearby 时必填)"),
                "y", ToolUtils.mapOf("type", "number", "description", "用户纬度 (action=query_nearby 时必填)"),
                "current", ToolUtils.mapOf("type", "integer", "description", "页码, 默认1"),
                "pageSize", ToolUtils.mapOf("type", "integer", "description", "每页条数, 默认5")
        ));
        schema.put("required", ToolUtils.listOf("action"));

        return ToolDefinition.builder()
                .name("query_shop")
                .description("查询商户信息。支持按ID精确查询、按名称模糊搜索、按类型分页查询、按位置搜索附近商户。返回商户名称、评分、均价、地址、营业时间等信息。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "");
        int current = args.containsKey("current") ? ((Number) args.get("current")).intValue() : 1;
        int pageSize = args.containsKey("pageSize") ? ((Number) args.get("pageSize")).intValue() : SystemConstants.DEFAULT_PAGE_SIZE;

        try {
            switch (action) {
                case "query_by_id": {
                    Long shopId = toLong(args.get("shopId"));
                    if (shopId == null) return ToolResult.fail("shopId 参数不能为空");
                    Shop shop = shopService.getById(shopId);
                    if (shop == null) return ToolResult.ok("未找到该商户");
                    return ToolResult.ok(formatShop(shop));
                }
                case "query_by_name": {
                    String name = (String) args.get("name");
                    if (StrUtil.isBlank(name)) return ToolResult.fail("name 参数不能为空");
                    Page<Shop> page = shopService.query()
                            .like("name", name)
                            .page(new Page<>(current, pageSize));
                    return ToolResult.ok(formatShopList(page.getRecords(), page.getTotal()));
                }
                case "query_by_type": {
                    Integer typeId = toInt(args.get("typeId"));
                    if (typeId == null) return ToolResult.fail("typeId 参数不能为空");
                    Page<Shop> page = shopService.query()
                            .eq("type_id", typeId)
                            .orderByDesc("score")
                            .page(new Page<>(current, pageSize));
                    return ToolResult.ok(formatShopList(page.getRecords(), page.getTotal()));
                }
                case "query_nearby": {
                    Double x = toDouble(args.get("x"));
                    Double y = toDouble(args.get("y"));
                    Integer typeId = toInt(args.get("typeId"));
                    if (x == null || y == null) return ToolResult.fail("x, y 参数不能为空");
                    // delegate to existing geo search
                    com.hmdp.dto.Result result = shopService.queryShopByType(
                            typeId != null ? typeId : 1, current, x, y);
                    if (result.getSuccess() && result.getData() != null) {
                        @SuppressWarnings("unchecked")
                        List<Shop> shops = (List<Shop>) result.getData();
                        return ToolResult.ok(formatShopList(shops, shops.size()));
                    }
                    return ToolResult.ok("未找到附近商户");
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: query_by_id, query_by_name, query_by_type, query_nearby");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询商户失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String formatShopList(List<Shop> shops, long total) {
        if (shops == null || shops.isEmpty()) return "未找到匹配的商户";
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(total).append(" 家商户:\n");
        int idx = 1;
        for (Shop s : shops) {
            sb.append(idx++).append(". ").append(s.getName())
                    .append(" | 评分:").append(s.getScore() != null ? s.getScore() / 10.0 : "暂无")
                    .append(" | 均价:¥").append(s.getAvgPrice() != null ? s.getAvgPrice() : "暂无")
                    .append(" | 地址:").append(s.getAddress() != null ? s.getAddress() : "暂无")
                    .append(" | ID:").append(s.getId()).append("\n");
        }
        return sb.toString();
    }

    private String formatShop(Shop s) {
        return s.getName() +
                " | 评分:" + (s.getScore() != null ? s.getScore() / 10.0 : "暂无") +
                " | 均价:¥" + (s.getAvgPrice() != null ? s.getAvgPrice() : "暂无") +
                " | 商圈:" + (s.getArea() != null ? s.getArea() : "暂无") +
                " | 地址:" + (s.getAddress() != null ? s.getAddress() : "暂无") +
                " | 营业时间:" + (s.getOpenHours() != null ? s.getOpenHours() : "暂无") +
                " | 销量:" + (s.getSold() != null ? s.getSold() : 0) +
                " | 评论数:" + (s.getComments() != null ? s.getComments() : 0) +
                " | ID:" + s.getId();
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

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
