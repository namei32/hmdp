package com.hmdp.ai.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 8 compatible map/list builders (since Map.of/List.of are Java 9+).
 */
public final class ToolUtils {

    private ToolUtils() {}

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be key-value pairs");
        }
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put((K) kvPairs[i], (V) kvPairs[i + 1]);
        }
        return map;
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return Collections.unmodifiableList(Arrays.asList(items));
    }

    public static Map<String, Object> emptyMap() {
        return Collections.emptyMap();
    }

    public static List<Object> emptyList() {
        return Collections.emptyList();
    }
}
