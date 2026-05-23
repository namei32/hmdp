package com.hmdp.ai.controller;

import com.hmdp.ai.conversation.Conversation;
import com.hmdp.ai.conversation.ConversationService;
import com.hmdp.ai.service.AiCustomerService;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@ConditionalOnProperty(prefix = "hmdp.ai", name = "enabled", havingValue = "true")
@RequestMapping({"/api/cs", "/cs"})
public class CustomerServiceController {

    private final AiCustomerService aiCustomerService;
    private final ConversationService conversationService;

    public CustomerServiceController(
            AiCustomerService aiCustomerService,
            ConversationService conversationService) {
        this.aiCustomerService = aiCustomerService;
        this.conversationService = conversationService;
    }

    /**
     * Send a chat message, receive SSE stream response.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String conversationId = body.get("conversationId");

        if (message == null || message.trim().isEmpty()) {
            SseEmitter errEmitter = new SseEmitter(3000L);
            try {
                errEmitter.send(SseEmitter.event().name("error").data(ToolUtils.mapOf("message", "消息不能为空")));
                errEmitter.complete();
            } catch (IOException e) {
                errEmitter.completeWithError(e);
            }
            return errEmitter;
        }

        // 5 minute timeout
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> log.debug("SSE completed"));
        emitter.onTimeout(() -> log.debug("SSE timed out"));
        emitter.onError(e -> log.debug("SSE error: {}", e.getMessage()));

        aiCustomerService.handleChat(conversationId, message.trim(), emitter);
        return emitter;
    }

    /**
     * List conversations for the current user.
     */
    @GetMapping("/conversations")
    public Map<String, Object> getConversations() {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        if (userId == null) {
            return ToolUtils.mapOf("success", true, "data", ToolUtils.listOf());
        }
        List<Conversation> convs = conversationService.getUserConversations(userId);
        // Return lightweight summaries
        List<Map<String, Object>> summaries = convs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("lastActiveAt", c.getLastActiveAt());
            m.put("createdAt", c.getCreatedAt());
            // Extract first user message as title
            String title = "新对话";
            if (c.getMessages() != null && !c.getMessages().isEmpty()) {
                for (Conversation.Message msg : c.getMessages()) {
                    if ("user".equals(msg.getRole())) {
                        title = msg.getContent();
                        if (title.length() > 20) {
                            title = title.substring(0, 20) + "...";
                        }
                        break;
                    }
                }
            }
            m.put("title", title);
            m.put("messageCount", c.getMessages() != null ? c.getMessages().size() : 0);
            return m;
        }).collect(Collectors.toList());
        return ToolUtils.mapOf("success", true, "data", summaries);
    }

    /**
     * Get a single conversation's full message history.
     */
    @GetMapping("/conversation/{id}")
    public Map<String, Object> getConversation(@PathVariable("id") String id) {
        Conversation conv = conversationService.getConversation(id);
        if (conv == null) {
            return ToolUtils.mapOf("success", false, "error", "对话不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", conv);
        return result;
    }

    /**
     * Delete a conversation.
     */
    @DeleteMapping("/conversation/{id}")
    public Map<String, Object> deleteConversation(@PathVariable("id") String id) {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        conversationService.deleteConversation(id, userId);
        return ToolUtils.mapOf("success", true);
    }
}
