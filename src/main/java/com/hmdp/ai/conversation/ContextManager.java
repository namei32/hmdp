package com.hmdp.ai.conversation;

import com.hmdp.ai.tool.ToolUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the conversation context window for LLM calls.
 * Converts internal Conversation.Message list to Anthropic API message format.
 * Trims older messages when exceeding max context length.
 */
@Component
public class ContextManager {

    private static final int DEFAULT_MAX_MESSAGES = 20;

    /**
     * Build the messages array for Anthropic API from conversation history.
     * Enforces max message limit by keeping the most recent messages.
     */
    public List<Map<String, Object>> buildMessages(Conversation conv, int maxMessages) {
        List<Conversation.Message> allMessages = conv.getMessages();
        if (allMessages == null || allMessages.isEmpty()) {
            return new ArrayList<>();
        }

        int limit = maxMessages > 0 ? maxMessages : DEFAULT_MAX_MESSAGES;
        List<Conversation.Message> recent;
        if (allMessages.size() > limit) {
            recent = allMessages.subList(allMessages.size() - limit, allMessages.size());
        } else {
            recent = allMessages;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Conversation.Message msg : recent) {
            messages.add(convertToAnthropic(msg));
        }
        return messages;
    }

    /**
     * Convert a single internal message to Anthropic format.
     */
    private Map<String, Object> convertToAnthropic(Conversation.Message msg) {
        String role = msg.getRole();
        // Map internal roles to Anthropic roles
        if ("tool".equals(role)) {
            return ToolUtils.mapOf("role", "user", "content", ToolUtils.listOf(
                    ToolUtils.mapOf("type", "tool_result",
                            "tool_use_id", msg.getToolName() != null ? msg.getToolName() : "unknown",
                            "content", msg.getContent() != null ? msg.getContent() : "")
            ));
        }
        return ToolUtils.mapOf("role", role, "content", msg.getContent() != null ? msg.getContent() : "");
    }

    /**
     * Check if there's enough conversation history to answer context-dependent questions.
     */
    public boolean hasContext(Conversation conv) {
        return conv != null && conv.getMessages() != null && conv.getMessages().size() > 1;
    }
}
