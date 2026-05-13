package com.hmdp.ai.mcp;

/**
 * MCP protocol message type constants (JSON-RPC 2.0 based).
 */
public final class McpProtocol {

    private McpProtocol() {}

    public static final String JSONRPC_VERSION = "2.0";

    // Method names
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_INITIALIZED = "notifications/initialized";

    // SSE event types
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_TOOL_CALL = "tool_call";
    public static final String EVENT_TOOL_RESULT = "tool_result";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DONE = "done";
}
