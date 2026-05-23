package com.hmdp.ai.tool;

import cn.hutool.json.JSONUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolJsonUtils {

    private ToolJsonUtils() {
    }

    public static Map<String, Object> object(Object... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be key-value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return map;
    }

    public static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public static String empty(String type, String message) {
        return json(object(
                "type", type,
                "found", false,
                "total", 0,
                "message", message
        ));
    }

    public static String list(String type, long total, List<Map<String, Object>> items) {
        return json(object(
                "type", type,
                "found", items != null && !items.isEmpty(),
                "total", total,
                "items", items
        ));
    }

    public static String detail(String type, Map<String, Object> data) {
        return json(object(
                "type", type,
                "found", data != null && !data.isEmpty(),
                "data", data
        ));
    }

    public static String error(String toolName, String message) {
        return json(object(
                "type", "tool_error",
                "tool", toolName,
                "success", false,
                "message", message
        ));
    }

    public static String json(Map<String, Object> map) {
        return JSONUtil.toJsonStr(map);
    }
}
