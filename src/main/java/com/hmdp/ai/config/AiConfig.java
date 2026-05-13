package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.ai")
public class AiConfig {
    /** Anthropic API key */
    private String apiKey = "sk-5883cca800084764b2690d877ee1ccfb";

    /** Anthropic API base URL */
    private String baseUrl = "https://api.deepseek.com";

    /** Model to use */
    private String model = "deepseek-v4-flash";

    /** Max tokens for the response */
    private int maxTokens = 1024;

    /** Max messages to keep in context window */
    private int contextMaxMessages = 20;

    /** Conversation TTL in Redis (seconds) */
    private int conversationTtl = 1800;

    /** Anthropic API version */
    private String apiVersion = "2026-06-01";
}
