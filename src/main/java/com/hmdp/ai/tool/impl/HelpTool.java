package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HelpTool implements McpTool {

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf());
        schema.put("required", ToolUtils.listOf());

        return ToolDefinition.builder()
                .name("get_help")
                .description("获取客服支持能力列表，了解可以咨询哪些问题。当用户询问'你能做什么'、'有什么功能'时调用此工具。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String help =
                "我是黑马点评智能客服，可以帮您：\n\n" +
                "1. 【查商户】按名称搜索、按类型筛选、查看附近商户、商户详情\n" +
                "   示例： \"帮我查火锅店\"、\"附近有什么好吃的\"、\"海底捞的评分怎么样\"\n\n" +
                "2. 【查优惠券】查看店铺优惠券列表、秒杀券信息、券详情\n" +
                "   示例： \"这家店有什么优惠\"、\"最近有秒杀活动吗\"\n\n" +
                "3. 【查订单】查看我的订单列表、按状态筛选、订单详情\n" +
                "   示例： \"我的订单\"、\"有哪些已支付的订单\"\n\n" +
                "4. 【查笔记】热门探店笔记、笔记详情、用户笔记\n" +
                "   示例： \"有什么热门探店\"、\"这篇笔记写了什么\"\n\n" +
                "5. 【用户信息】查看我的个人信息、用户详情\n" +
                "   示例： \"我的信息\"\n\n" +
                "直接告诉我您的需求，我来帮您查询！";
        return ToolResult.ok(help);
    }
}
