package com.hmdp.ai.tool.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class QueryUserTool implements McpTool {

    private final IUserService userService;
    private final IUserInfoService userInfoService;

    public QueryUserTool(IUserService userService, IUserInfoService userInfoService) {
        this.userService = userService;
        this.userInfoService = userInfoService;
    }

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
                    Map<String, Object> data = ToolJsonUtils.object(
                            "id", currentUser.getId(),
                            "nickName", currentUser.getNickName(),
                            "icon", currentUser.getIcon()
                    );
                    if (info != null) {
                        ToolJsonUtils.putIfNotNull(data, "gender", info.getGender() != null ? (info.getGender() ? "女" : "男") : null);
                        ToolJsonUtils.putIfNotNull(data, "birthday", info.getBirthday());
                        ToolJsonUtils.putIfNotNull(data, "city", info.getCity());
                        ToolJsonUtils.putIfNotNull(data, "introduce", info.getIntroduce());
                        ToolJsonUtils.putIfNotNull(data, "fans", info.getFans());
                        ToolJsonUtils.putIfNotNull(data, "followee", info.getFollowee());
                    }
                    return ToolResult.ok(ToolJsonUtils.detail("current_user", data));
                }
                case "get_user_info": {
                    Long userId = toLong(args.get("userId"));
                    if (userId == null) {
                        return ToolResult.fail("userId 参数不能为空");
                    }
                    User user = userService.getById(userId);
                    if (user == null) {
                        return ToolResult.ok(ToolJsonUtils.empty("user_detail", "未找到该用户"));
                    }
                    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                    Map<String, Object> data = ToolJsonUtils.object(
                            "id", userDTO.getId(),
                            "nickName", userDTO.getNickName(),
                            "icon", userDTO.getIcon()
                    );
                    return ToolResult.ok(ToolJsonUtils.detail("user_detail", data));
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: get_my_info, get_user_info");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询用户信息失败: " + e.getMessage());
        }
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
