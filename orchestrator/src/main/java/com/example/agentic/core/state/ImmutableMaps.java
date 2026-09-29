package com.example.agentic.core.state;

import java.util.HashMap;
import java.util.Map;

/** Null-free map copies; null keys and values carry no meaning in artifacts and are dropped. */
public final class ImmutableMaps {

    private ImmutableMaps() {
    }

    /** A new map without null keys or values; callers wrap it in {@link Map#copyOf} to make it immutable. */
    public static <V> Map<String, V> withoutNulls(Map<String, V> source) {
        Map<String, V> copy = new HashMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        return copy;
    }
}
