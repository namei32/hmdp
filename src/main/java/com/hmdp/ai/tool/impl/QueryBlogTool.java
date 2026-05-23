package com.hmdp.ai.tool.impl;

import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolJsonUtils;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryBlogTool implements McpTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IBlogService blogService;

    public QueryBlogTool(IBlogService blogService) {
        this.blogService = blogService;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ToolUtils.mapOf(
                "action", ToolUtils.mapOf("type", "string", "description", "操作类型: hot_blogs | blog_detail | user_blogs"),
                "current", ToolUtils.mapOf("type", "integer", "description", "页码, 默认1 (action=hot_blogs 时使用)"),
                "blogId", ToolUtils.mapOf("type", "integer", "description", "笔记ID (action=blog_detail 时必填)"),
                "userId", ToolUtils.mapOf("type", "integer", "description", "用户ID (action=user_blogs 时必填)")
        ));
        schema.put("required", ToolUtils.listOf("action"));

        return ToolDefinition.builder()
                .name("query_blog")
                .description("查询探店笔记。可按热度排行查询、按ID查看笔记详情、或按用户ID查询其发表的笔记。返回笔记标题、内容摘要、点赞数、评论数等。")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String action = (String) args.getOrDefault("action", "");
        int current = args.containsKey("current") ? ((Number) args.get("current")).intValue() : 1;

        try {
            switch (action) {
                case "hot_blogs": {
                    com.hmdp.dto.Result result = blogService.queryHotBlog(current);
                    if (result.getSuccess() && result.getData() != null) {
                        @SuppressWarnings("unchecked")
                        List<Blog> blogs = (List<Blog>) result.getData();
                        return ToolResult.ok(formatBlogList(blogs));
                    }
                    return ToolResult.ok(ToolJsonUtils.empty("blog_list", "暂无热门笔记"));
                }
                case "blog_detail": {
                    Long blogId = toLong(args.get("blogId"));
                    if (blogId == null) {
                        return ToolResult.fail("blogId 参数不能为空");
                    }
                    com.hmdp.dto.Result result = blogService.queryBlogById(blogId);
                    if (result.getSuccess() && result.getData() != null) {
                        Blog blog = (Blog) result.getData();
                        return ToolResult.ok(formatBlogDetail(blog));
                    }
                    return ToolResult.ok(ToolJsonUtils.empty("blog_detail", "未找到该笔记"));
                }
                case "user_blogs": {
                    Long userId = toLong(args.get("userId"));
                    if (userId == null) {
                        return ToolResult.fail("userId 参数不能为空");
                    }
                    List<Blog> blogs = blogService.query()
                            .eq("user_id", userId)
                            .orderByDesc("create_time")
                            .last("LIMIT 10")
                            .list();
                    return ToolResult.ok(formatBlogList(blogs));
                }
                default:
                    return ToolResult.fail("不支持的操作: " + action + "，支持: hot_blogs, blog_detail, user_blogs");
            }
        } catch (Exception e) {
            return ToolResult.fail("查询笔记失败: " + e.getMessage());
        }
    }

    private String formatBlogList(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return ToolJsonUtils.empty("blog_list", "暂无笔记");
        }
        List<Map<String, Object>> items = new ArrayList<>(blogs.size());
        for (Blog b : blogs) {
            Map<String, Object> item = ToolJsonUtils.object(
                    "id", b.getId(),
                    "title", b.getTitle(),
                    "liked", b.getLiked() != null ? b.getLiked() : 0,
                    "comments", b.getComments() != null ? b.getComments() : 0
            );
            if (b.getCreateTime() != null) {
                item.put("createTime", b.getCreateTime().format(FMT));
            }
            items.add(item);
        }
        return ToolJsonUtils.list("blog_list", blogs.size(), items);
    }

    private String formatBlogDetail(Blog b) {
        Map<String, Object> data = ToolJsonUtils.object(
                "id", b.getId(),
                "title", b.getTitle(),
                "content", b.getContent() != null && b.getContent().length() > 200
                        ? b.getContent().substring(0, 200) + "..."
                        : b.getContent(),
                "liked", b.getLiked() != null ? b.getLiked() : 0,
                "comments", b.getComments() != null ? b.getComments() : 0
        );
        if (b.getCreateTime() != null) {
            data.put("createTime", formatTime(b.getCreateTime()));
        }
        return ToolJsonUtils.detail("blog_detail", data);
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
}
