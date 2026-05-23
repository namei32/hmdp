package com.hmdp.ai.service;

import com.hmdp.ai.config.AiConfig;
import com.hmdp.ai.conversation.Conversation;
import com.hmdp.ai.conversation.ConversationService;
import com.hmdp.ai.tool.ToolUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "hmdp.ai", name = "enabled", havingValue = "true")
public class AiCustomerService {

    private final ChatClient chatClient;
    private final AiConfig aiConfig;
    private final ConversationService conversationService;

    private static final String SYSTEM_PROMPT =
            "你是黑马点评平台的智能客服助手“小点”。你可以帮助用户：\n" +
            "- 查询商户信息（按名称、类型、位置搜索）\n" +
            "- 查看优惠券和秒杀活动\n" +
            "- 查询订单状态（需要用户已登录）\n" +
            "- 浏览探店笔记和热门内容\n" +
            "- 解答平台使用问题\n\n" +
            "回答要求：\n" +
            "1. 简洁准确，使用中文回答。\n" +
            "2. 可以调用工具查询平台真实数据，但不要向用户暴露工具名、函数名、参数或内部调用过程。\n" +
            "3. 查询到商户后，列出商户名称、评分、均价、地址等关键信息，并保留商户ID，便于用户追问。\n" +
            "4. 涉及订单等敏感信息时，如果用户未登录，请引导用户先登录。\n" +
            "5. 涉及价格、时间、库存等信息务必基于工具结果，不要编造。\n" +
            "6. 如果问题超出能力范围，请诚实说明并建议联系人工客服。";

    public AiCustomerService(
            ChatClient.Builder chatClientBuilder,
            HmdpCustomerTools hmdpCustomerTools,
            AiConfig aiConfig,
            ConversationService conversationService) {
        this.chatClient = chatClientBuilder
                .defaultTools(hmdpCustomerTools)
                .build();
        this.aiConfig = aiConfig;
        this.conversationService = conversationService;
    }

    /**
     * Handle a chat message and stream the final Spring AI response via SSE.
     */
    public void handleChat(String conversationId, String userMessage, SseEmitter emitter) {
        UserDTO currentUser = UserHolder.getUser();

        CompletableFuture.runAsync(() -> {
            if (currentUser != null) {
                UserHolder.saveUser(currentUser);
            }
            try {
                Long userId = currentUser != null ? currentUser.getId() : null;
                Conversation conv = getOrCreateConversation(conversationId, userId);

                conv.addMessage(Conversation.Message.builder()
                        .role("user")
                        .content(userMessage)
                        .timestamp(System.currentTimeMillis())
                        .build());

                String assistantContent = callSpringAi(conv);

                if (assistantContent != null && !assistantContent.trim().isEmpty()) {
                    conv.addMessage(Conversation.Message.builder()
                            .role("assistant")
                            .content(assistantContent)
                            .timestamp(System.currentTimeMillis())
                            .build());
                    sendSseEvent(emitter, "text", ToolUtils.mapOf("content", assistantContent));
                }

                conversationService.saveConversation(conv);
                sendSseEvent(emitter, "meta", ToolUtils.mapOf("conversationId", conv.getId()));
                sendSseEvent(emitter, "done", "complete");
                emitter.complete();
            } catch (Exception e) {
                log.error("Spring AI chat handling error", e);
                try {
                    sendSseEvent(emitter, "error", ToolUtils.mapOf("message", normalizeErrorMessage(e)));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                UserHolder.removeUser();
            }
        });
    }

    private String callSpringAi(Conversation conv) {
        String prompt = buildConversationPrompt(conv);
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(aiConfig.getModel()).build())
                .call()
                .content();
        if (content == null || content.trim().isEmpty()) {
            return "抱歉，我暂时没有得到有效回复，请稍后再试。";
        }
        return content.trim();
    }

    private String buildConversationPrompt(Conversation conv) {
        List<Conversation.Message> recentMessages = recentMessages(conv);
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下是本次会话的最近上下文，请基于上下文回答最后一个用户问题。\n\n");
        for (Conversation.Message message : recentMessages) {
            if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) {
                continue;
            }
            if ("tool".equals(message.getRole())) {
                continue;
            }
            prompt.append("user".equals(message.getRole()) ? "用户：" : "客服：")
                    .append(message.getContent())
                    .append("\n");
        }
        prompt.append("\n请直接给出对用户自然、友好的回复。");
        return prompt.toString();
    }

    private List<Conversation.Message> recentMessages(Conversation conv) {
        List<Conversation.Message> messages = conv.getMessages();
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int maxMessages = Math.max(1, aiConfig.getContextMaxMessages());
        int fromIndex = Math.max(0, messages.size() - maxMessages);
        return messages.subList(fromIndex, messages.size());
    }

    private String normalizeErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "AI 服务暂时不可用，请稍后再试。";
        }
        if (message.contains("401") || message.toLowerCase().contains("api key")) {
            return "AI 服务密钥未配置或无效，请检查 HMDP_AI_API_KEY。";
        }
        if (message.toLowerCase().contains("timeout")) {
            return "AI 服务响应超时，请稍后再试。";
        }
        return "AI 服务暂时不可用：" + message;
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
            if (existing != null) {
                return existing;
            }
        }
        return conversationService.createConversation(userId);
    }
}
