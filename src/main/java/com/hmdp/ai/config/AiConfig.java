package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.ai")
public class AiConfig {
    /** Whether the AI customer service endpoints are enabled. */
    private boolean enabled = false;

    /** AI provider API key, read from HMDP_AI_API_KEY by default. */
    private String apiKey;

    /** AI provider API base URL. */
    private String baseUrl = "https://api.deepseek.com";

    /** Model to use */
    private String model = "deepseek-chat";

    /** Max tokens for the response */
    private int maxTokens = 1024;

    /** Max messages to keep in context window */
    private int contextMaxMessages = 20;

    /** Conversation TTL in Redis (seconds) */
    private int conversationTtl = 1800;

    /** Provider API version, retained for compatibility with older configs. */
    private String apiVersion = "2026-06-01";
}
