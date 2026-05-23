package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        List<Map<String, Object>> capabilities = new ArrayList<>();
        capabilities.add(ToolJsonUtils.object(
                "name", "查商户",
                "description", "按名称搜索、按类型筛选、查看附近商户、商户详情",
                "examples", ToolUtils.listOf("帮我查火锅店", "附近有什么好吃的", "海底捞的评分怎么样")
        ));
        capabilities.add(ToolJsonUtils.object(
                "name", "查优惠券",
                "description", "查看店铺优惠券列表、秒杀券信息、券详情",
                "examples", ToolUtils.listOf("这家店有什么优惠", "最近有秒杀活动吗")
        ));
        capabilities.add(ToolJsonUtils.object(
                "name", "查订单",
                "description", "查看我的订单列表、按状态筛选、订单详情",
                "examples", ToolUtils.listOf("我的订单", "有哪些已支付的订单")
        ));
        capabilities.add(ToolJsonUtils.object(
                "name", "查笔记",
                "description", "热门探店笔记、笔记详情、用户笔记",
                "examples", ToolUtils.listOf("有什么热门探店", "这篇笔记写了什么")
        ));
        capabilities.add(ToolJsonUtils.object(
                "name", "用户信息",
                "description", "查看我的个人信息、用户详情",
                "examples", ToolUtils.listOf("我的信息")
        ));

        return ToolResult.ok(ToolJsonUtils.json(ToolJsonUtils.object(
                "type", "help",
                "assistantName", "小点",
                "platform", "黑马点评",
                "capabilities", capabilities
        )));
    }
}
