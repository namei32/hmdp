package com.hmdp.ai.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    /** MCP tool name, unique identifier */
    private String name;
    /** Human-readable description for the LLM */
    private String description;
    /** JSON Schema for the tool's input parameters */
    private Map<String, Object> inputSchema;
}
