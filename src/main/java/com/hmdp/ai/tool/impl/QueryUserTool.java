package com.hmdp.ai.tool.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryUserTool implements McpTool {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf(
                "action", ToolUtils.mapOf("type", "string", "description", "操作类型: get_my_info | get_user_info"),
                "userId", ToolUtils.mapOf("type", "integer", "description", "用户ID (action=get_user_info 时必填)")
        ));
        schema.put("required", ToolUtils.listOf("action"));

        return ToolDefinition.builder()
                .name("query_user")
                .description("查询用户信息。可查询当前登录用户的信息（昵称、头像、个人详情等），或按用户ID查询其他用户的基本信息。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "");

        try {
            switch (action) {
                case "get_my_info": {
                    if (UserHolder.getUser() == null) {
                        return ToolResult.fail("请先登录后再查询个人信息");
                    }
                    UserDTO currentUser = UserHolder.getUser();
                    UserInfo info = userInfoService.getById(currentUser.getId());
                    StringBuilder sb = new StringBuilder();
                    sb.append("当前用户信息:\n");
                    sb.append("用户ID: ").append(currentUser.getId()).append("\n");
                    sb.append("昵称: ").append(currentUser.getNickName()).append("\n");
                    if (currentUser.getIcon() != null) sb.append("头像: ").append(currentUser.getIcon()).append("\n");
                    if (info != null) {
                        if (info.getGender() != null) sb.append("性别: ").append(info.getGender() ? "女" : "男").append("\n");
                        if (info.getBirthday() != null) sb.append("生日: ").append(info.getBirthday()).append("\n");
                        if (info.getCity() != null) sb.append("城市: ").append(info.getCity()).append("\n");
                        if (info.getIntroduce() != null) sb.append("简介: ").append(info.getIntroduce()).append("\n");
                        if (info.getFans() != null) sb.append("粉丝数: ").append(info.getFans()).append("\n");
                        if (info.getFollowee() != null) sb.append("关注数: ").append(info.getFollowee()).append("\n");
                    }
                    return ToolResult.ok(sb.toString());
                }
                case "get_user_info": {
                    Long userId = toLong(args.get("userId"));
                    if (userId == null) return ToolResult.fail("userId 参数不能为空");
                    User user = userService.getById(userId);
                    if (user == null) return ToolResult.ok("未找到该用户");
                    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                    StringBuilder sb = new StringBuilder();
                    sb.append("用户信息:\n");
                    sb.append("用户ID: ").append(userDTO.getId()).append("\n");
                    sb.append("昵称: ").append(userDTO.getNickName()).append("\n");
                    if (userDTO.getIcon() != null) sb.append("头像: ").append(userDTO.getIcon()).append("\n");
                    return ToolResult.ok(sb.toString());
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: get_my_info, get_user_info");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询用户信息失败: " + e.getMessage());
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.valueOf(val.toString()); } catch (NumberFormatException e) { return null; }
    }
}
