package com.hmdp.ai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    private final List<McpTool> toolBeans;

    public ToolRegistry(List<McpTool> toolBeans) {
        this.toolBeans = toolBeans;
    }

    @PostConstruct
    public void init() {
        for (McpTool tool : toolBeans) {
            register(tool);
        }
        log.info("ToolRegistry auto-registered {} tools", tools.size());
    }

    public void register(McpTool tool) {
        tools.put(tool.getDefinition().getName(), tool);
    }

    public McpTool get(String name) {
        return tools.get(name);
    }

    public List<McpTool> listAll() {
        return new ArrayList<>(tools.values());
    }

    public List<ToolDefinition> listDefinitions() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (McpTool tool : tools.values()) {
            defs.add(tool.getDefinition());
        }
        return defs;
    }
}
