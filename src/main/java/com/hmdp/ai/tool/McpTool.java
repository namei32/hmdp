package com.hmdp.ai.tool;

import java.util.Map;

/**
 * Interface for MCP tools that wrap business capabilities.
 * Each tool exposes its definition (name, description, input schema)
 * and an execute method that takes arguments and returns a result.
 */
public interface McpTool {

    ToolDefinition getDefinition();

    ToolResult execute(Map<String, Object> args);
}
