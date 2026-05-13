package com.hmdp.ai.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    private String id;
    private Long userId;
    private List<Message> messages;
    private String summary;
    private long createdAt;
    private long lastActiveAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;    // "user" | "assistant" | "tool"
        private String content;
        private String toolName;
        private long timestamp;
    }

    public static Conversation create(String id, Long userId) {
        long now = System.currentTimeMillis();
        return Conversation.builder()
                .id(id)
                .userId(userId)
                .messages(new ArrayList<>())
                .createdAt(now)
                .lastActiveAt(now)
                .build();
    }

    public void addMessage(Message msg) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(msg);
        lastActiveAt = System.currentTimeMillis();
    }
}
