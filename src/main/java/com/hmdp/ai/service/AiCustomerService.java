package com.hmdp.ai.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.ai.config.AiConfig;
import com.hmdp.ai.conversation.Conversation;
import com.hmdp.ai.conversation.ConversationService;
import com.hmdp.ai.conversation.ContextManager;
import com.hmdp.ai.mcp.McpServer;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.ai.tool.ToolResult;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AiCustomerService {

    @Resource
    private AiConfig aiConfig;

    @Resource
    private McpServer mcpServer;

    @Resource
    private ConversationService conversationService;

    @Resource
    private ContextManager contextManager;

    private static final String SYSTEM_PROMPT =
            "你是黑马点评平台的智能客服助手\"小点\"。你可以帮助用户：\n" +
            "- 查询商户信息（按名称、类型、位置搜索）\n" +
            "- 查看优惠券和秒杀活动\n" +
            "- 查询订单状态（需要用户已登录）\n" +
            "- 浏览探店笔记和热门内容\n" +
            "- 解答平台使用问题\n" +
            "\n" +
            "回答要求：\n" +
            "1. 简洁准确，用中文回复\n" +
            "2. 涉及订单等敏感信息时，如果用户未登录，请引导用户先登录\n" +
            "3. 查询到商户后，列出商户名称、评分、均价等关键信息，并保留商户ID\n" +
            "——这样用户在追问时可以精确定位\n" +
            "4. 涉及价格、时间等信息务必准确，不要编造\n" +
            "5. 如果用户问的问题超出你的能力范围，诚实告知并建议用户联系人工客服";

    /**
     * Handle a chat message and stream the response via SSE.
     */
    public void handleChat(String conversationId, String userMessage, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
                Conversation conv = getOrCreateConversation(conversationId, userId);

                // Add user message
                conv.addMessage(Conversation.Message.builder()
                        .role("user")
                        .content(userMessage)
                        .timestamp(System.currentTimeMillis())
                        .build());

                // Build request
                List<Map<String, Object>> messages = contextManager.buildMessages(conv,
                        aiConfig.getContextMaxMessages());

                // Call Claude and handle tool loop
                String assistantContent = callClaudeWithTools(messages, emitter, conv);

                // Save assistant response
                if (assistantContent != null && !assistantContent.isEmpty()) {
                    conv.addMessage(Conversation.Message.builder()
                            .role("assistant")
                            .content(assistantContent)
                            .timestamp(System.currentTimeMillis())
                            .build());
                }

                conversationService.saveConversation(conv);

                // Send conversationId to client
                Map<String, String> meta = ToolUtils.mapOf("conversationId", conv.getId());
                SseEmitter.SseEventBuilder metaEvent = SseEmitter.event()
                        .name("meta")
                        .data(meta);
                try { emitter.send(metaEvent); } catch (Exception ignored) {}

                // Signal done
                SseEmitter.SseEventBuilder doneEvent = SseEmitter.event()
                        .name("done")
                        .data("complete");
                try { emitter.send(doneEvent); } catch (Exception ignored) {}
                emitter.complete();

            } catch (Exception e) {
                log.error("Chat handling error", e);
                try {
                    SseEmitter.SseEventBuilder errEvent = SseEmitter.event()
                            .name("error")
                            .data(ToolUtils.mapOf("message", "处理请求时出错: " + e.getMessage()));
                    emitter.send(errEvent);
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
    }

    /**
     * Call Claude API with tools. Handle tool-use loop.
     * Returns the final text response.
     */
    private String callClaudeWithTools(List<Map<String, Object>> messages, SseEmitter emitter, Conversation conv)
            throws Exception {
        List<Map<String, Object>> tools = mcpServer.getToolsForAnthropic();
        List<Map<String, Object>> currentMessages = new ArrayList<>(messages);
        StringBuilder fullResponse = new StringBuilder();
        int maxLoops = 5;

        for (int loop = 0; loop < maxLoops; loop++) {
            JSONObject requestBody = buildRequest(currentMessages, tools);

            log.debug("Claude request: messages={}, tools={}", currentMessages.size(), tools.size());
            String responseJson = callAnthropicApi(requestBody);

            JSONObject response = JSONUtil.parseObj(responseJson);

            // Check for errors
            if (response.containsKey("error")) {
                JSONObject err = response.getJSONObject("error");
                String errMsg = err.getStr("message", "Unknown API error");
                sendSseEvent(emitter, "error", ToolUtils.mapOf("message", errMsg));
                return fullResponse.length() > 0 ? fullResponse.toString() : "抱歉，我遇到了一些问题，请稍后再试。";
            }

            // Parse content blocks
            JSONArray content = response.getJSONArray("content");
            if (content == null || content.isEmpty()) {
                log.warn("Empty content from Claude");
                return fullResponse.length() > 0 ? fullResponse.toString() : "抱歉，我暂时无法回答这个问题。";
            }

            // Check if there are tool_use blocks
            List<JSONObject> toolUses = new ArrayList<>();
            StringBuilder textBlock = new StringBuilder();

            for (int i = 0; i < content.size(); i++) {
                JSONObject block = content.getJSONObject(i);
                String type = block.getStr("type");
                if ("tool_use".equals(type)) {
                    toolUses.add(block);
                } else if ("text".equals(type)) {
                    String text = block.getStr("text", "");
                    if (!text.isEmpty()) {
                        textBlock.append(text);
                        sendSseEvent(emitter, "text", ToolUtils.mapOf("content", text));
                    }
                }
            }

            fullResponse.append(textBlock);

            // If no tool calls, we're done
            if (toolUses.isEmpty()) {
                return fullResponse.toString();
            }

            // Execute tools
            // Add assistant message with tool_use blocks
            List<Map<String, Object>> assistantContentBlocks = new ArrayList<>();
            if (textBlock.length() > 0) {
                assistantContentBlocks.add(ToolUtils.mapOf("type", "text", "text", textBlock.toString()));
            }

            List<Map<String, Object>> toolResultBlocks = new ArrayList<>();
            for (JSONObject toolUse : toolUses) {
                String toolName = toolUse.getStr("name");
                String toolId = toolUse.getStr("id");
                @SuppressWarnings("unchecked")
                Map<String, Object> toolInput = (Map<String, Object>) toolUse.get("input");

                log.info("Claude requested tool: {} with args: {}", toolName, toolInput);

                // Notify frontend
                sendSseEvent(emitter, "tool_call", ToolUtils.mapOf("tool", toolName, "input", toolInput != null ? toolInput : ToolUtils.mapOf()));

                // Execute tool
                ToolResult result = mcpServer.callTool(toolName, toolInput != null ? toolInput : ToolUtils.mapOf());
                String resultContent = result.isSuccess() ? result.getContent() : result.getError();

                // Send tool result to frontend
                sendSseEvent(emitter, "tool_result", ToolUtils.mapOf("tool", toolName, "result", resultContent));

                // Build Anthropic tool_use and tool_result content blocks
                Map<String, Object> toolUseBlock = new HashMap<>();
                toolUseBlock.put("type", "tool_use");
                toolUseBlock.put("id", toolId);
                toolUseBlock.put("name", toolName);
                toolUseBlock.put("input", toolInput != null ? toolInput : ToolUtils.mapOf());
                assistantContentBlocks.add(toolUseBlock);

                Map<String, Object> toolResultBlock = new HashMap<>();
                toolResultBlock.put("type", "tool_result");
                toolResultBlock.put("tool_use_id", toolId);
                toolResultBlock.put("content", resultContent);
                toolResultBlocks.add(toolResultBlock);
            }

            // Add assistant message with tool_use blocks
            currentMessages.add(ToolUtils.mapOf("role", "assistant", "content", assistantContentBlocks));

            // Add user message with tool_result blocks
            currentMessages.add(ToolUtils.mapOf("role", "user", "content", toolResultBlocks));
        }

        return fullResponse.length() > 0 ? fullResponse.toString() : "抱歉，处理超时，请稍后重试。";
    }

    private JSONObject buildRequest(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        JSONObject req = new JSONObject();
        req.set("model", aiConfig.getModel());
        req.set("max_tokens", aiConfig.getMaxTokens());

        // Build system + messages
        req.set("system", SYSTEM_PROMPT);

        List<Map<String, Object>> allMessages = new ArrayList<>(messages);

        req.set("messages", allMessages);
        req.set("tools", tools);

        // Enable streaming
        req.set("stream", false); // We process sequentially for tool loop

        return req;
    }

    /**
     * Call Anthropic Messages API via HTTP.
     */
    private String callAnthropicApi(JSONObject requestBody) throws Exception {
        String url = aiConfig.getBaseUrl() + "/v1/messages";
        String apiKey = aiConfig.getApiKey();

        if (apiKey == null || apiKey.isEmpty() || "your-api-key".equals(apiKey)) {
            JSONObject err = new JSONObject();
            err.set("error", ToolUtils.mapOf("message", "AI service not configured: missing API key"));
            return err.toString();
        }

        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(url);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", aiConfig.getApiVersion());
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);

            // Write request body
            byte[] bodyBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            // Read response
            int status = conn.getResponseCode();
            java.io.InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();

            StringBuilder resp = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    resp.append(line);
                }
            }

            if (status >= 400) {
                log.error("Claude API error: status={}, body={}", status, resp);
                JSONObject err = new JSONObject();
                // Try to parse the error response
                try {
                    JSONObject parsed = JSONUtil.parseObj(resp.toString());
                    JSONObject errorObj = parsed.getJSONObject("error");
                    String errorMsg = errorObj != null ? errorObj.getStr("message", "HTTP " + status) : ("HTTP " + status + ": " + resp);
                    cn.hutool.json.JSONObject errObj = new cn.hutool.json.JSONObject();
                    errObj.set("message", errorMsg);
                    err.set("error", errObj);
                } catch (Exception e) {
                    cn.hutool.json.JSONObject errObj2 = new cn.hutool.json.JSONObject();
                    errObj2.set("message", "HTTP " + status + ": " + resp.substring(0, Math.min(200, resp.length())));
                    err.set("error", errObj2);
                }
                return err.toString();
            }

            return resp.toString();
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private void sendSseEvent(SseEmitter emitter, String name, Object data) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event().name(name).data(data);
            emitter.send(event);
        } catch (Exception e) {
            log.debug("SSE send failed (client may have disconnected): {}", e.getMessage());
        }
    }

    private Conversation getOrCreateConversation(String convId, Long userId) {
        if (convId != null && !convId.isEmpty()) {
            Conversation existing = conversationService.getConversation(convId);
            if (existing != null) return existing;
        }
        return conversationService.createConversation(userId);
    }
}
