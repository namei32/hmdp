package com.hmdp.ai.mcp;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client MCP session. Holds session-scoped state (prompts, resources, etc.).
 */
public class McpSession {

    private final String sessionId;
    private final ConcurrentHashMap<String, Object> state;
    private volatile boolean initialized;
    private final long createdAt;

    public McpSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.state = new ConcurrentHashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void put(String key, Object value) {
        state.put(key, value);
    }

    public Object get(String key) {
        return state.get(key);
    }
}
