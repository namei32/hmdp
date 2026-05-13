package com.hmdp.ai.mcp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.McpTool;
import com.hmdp.ai.tool.ToolDefinition;
import com.hmdp.ai.tool.ToolRegistry;
import com.hmdp.ai.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded MCP Server. Manages MCP sessions and handles tool list/call requests.
 */
@Slf4j
@Component
public class McpServer {

    @Resource
    private ToolRegistry toolRegistry;

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("MCP Server initialized with {} tools", toolRegistry.listAll().size());
    }

    public McpSession createSession() {
        McpSession session = new McpSession();
        sessions.put(session.getSessionId(), session);
        log.debug("MCP session created: {}", session.getSessionId());
        return session;
    }

    public McpSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    public void registerEmitter(String sessionId, SseEmitter emitter) {
        emitters.put(sessionId, emitter);
    }

    /**
     * Handle a JSON-RPC message from the MCP client.
     */
    public JSONObject handleMessage(String sessionId, JSONObject request) {
        String method = request.getStr("method");
        String id = request.getStr("id");

        log.debug("MCP message: method={}, sessionId={}", method, sessionId);

        if (McpProtocol.METHOD_TOOLS_LIST.equals(method)) {
            return buildToolsListResponse(id);
        }
        if (McpProtocol.METHOD_TOOLS_CALL.equals(method)) {
            return handleToolCall(id, request.getJSONObject("params"));
        }
        if (McpProtocol.METHOD_INITIALIZE.equals(method)) {
            return buildInitializeResponse(id, sessionId);
        }

        return buildError(id, -32601, "Method not found: " + method);
    }

    private JSONObject buildInitializeResponse(String id, String sessionId) {
        McpSession session = getSession(sessionId);
        if (session != null) {
            session.setInitialized(true);
        }

        Map<String, Object> result = ToolUtils.mapOf(
                "protocolVersion", "2024-11-05",
                "serverInfo", ToolUtils.mapOf("name", "hmdp-customer-service", "version", "1.0.0"),
                "capabilities", ToolUtils.mapOf("tools", ToolUtils.mapOf())
        );

        return buildResponse(id, result);
    }

    private JSONObject buildToolsListResponse(String id) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpTool tool : toolRegistry.listAll()) {
            ToolDefinition def = tool.getDefinition();
            Map<String, Object> t = new HashMap<>();
            t.put("name", def.getName());
            t.put("description", def.getDescription());
            t.put("inputSchema", def.getInputSchema());
            tools.add(t);
        }
        return buildResponse(id, ToolUtils.mapOf("tools", tools));
    }

    private JSONObject handleToolCall(String id, JSONObject params) {
        if (params == null) {
            return buildError(id, -32602, "Missing params");
        }
        String toolName = params.getStr("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (toolName == null) {
            return buildError(id, -32602, "Missing tool name");
        }

        McpTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return buildError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            ToolResult result = tool.execute(arguments != null ? arguments : ToolUtils.mapOf());

            Map<String, Object> content = new HashMap<>();
            content.put("type", "text");
            content.put("text", result.getContent() != null ? result.getContent() : result.getError());

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("content", ToolUtils.listOf(content));
            resultMap.put("isError", !result.isSuccess());

            return buildResponse(id, resultMap);
        } catch (Exception e) {
            log.error("Tool execution error: {}", toolName, e);
            return buildError(id, -32000, "Tool execution error: " + e.getMessage());
        }
    }

    /**
     * Direct tool execution (used by AiCustomerService, bypasses JSON-RPC).
     */
    public ToolResult callTool(String toolName, Map<String, Object> args) {
        McpTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return ToolResult.fail("Unknown tool: " + toolName);
        }
        return tool.execute(args);
    }

    /**
     * Get all tool definitions in Anthropic API format.
     */
    public List<Map<String, Object>> getToolsForAnthropic() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpTool tool : toolRegistry.listAll()) {
            ToolDefinition def = tool.getDefinition();
            Map<String, Object> t = new HashMap<>();
            t.put("name", def.getName());
            t.put("description", def.getDescription());
            t.put("input_schema", def.getInputSchema());
            tools.add(t);
        }
        return tools;
    }

    private JSONObject buildResponse(String id, Map<String, Object> result) {
        JSONObject resp = new JSONObject();
        resp.set("jsonrpc", McpProtocol.JSONRPC_VERSION);
        resp.set("id", id);
        resp.set("result", result);
        return resp;
    }

    private JSONObject buildError(String id, int code, String message) {
        JSONObject resp = new JSONObject();
        resp.set("jsonrpc", McpProtocol.JSONRPC_VERSION);
        resp.set("id", id);
        resp.set("error", ToolUtils.mapOf("code", code, "message", message));
        return resp;
    }
}
