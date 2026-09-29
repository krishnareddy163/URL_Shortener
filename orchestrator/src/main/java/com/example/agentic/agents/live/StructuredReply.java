package com.example.agentic.agents.live;

import com.example.agentic.core.agent.AgentException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates the JSON object a live agent must return. Anything malformed becomes an
 * {@link AgentException}, which the engine counts as a failed attempt (and eventually falls back).
 */
final class StructuredReply {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() { };

    private StructuredReply() {
    }

    static Map<String, Object> parse(String text) throws AgentException {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AgentException("malformed model output: no JSON object found");
        }
        try {
            return JSON.readValue(text.substring(start, end + 1), JSON_MAP);
        } catch (IOException _) {
            throw new AgentException("malformed model output: invalid JSON");
        }
    }

    static String requireString(Map<String, Object> reply, String field) throws AgentException {
        if (!(reply.get(field) instanceof String value) || value.isBlank()) {
            throw new AgentException("malformed model output: '" + field + "' must be a non-empty string");
        }
        return value;
    }

    static List<Object> requireList(Map<String, Object> reply, String field, boolean nonEmpty) throws AgentException {
        if (!(reply.get(field) instanceof List<?> list) || (nonEmpty && list.isEmpty())) {
            throw new AgentException("malformed model output: '" + field + "' must be a" + (nonEmpty ? " non-empty" : "") + " list");
        }
        return new ArrayList<>(list);
    }

    static void requireOneOf(Map<?, ?> object, String field, Set<String> allowed) throws AgentException {
        if (!allowed.contains(String.valueOf(object.get(field)))) {
            throw new AgentException("malformed model output: '" + field + "' must be one of " + allowed);
        }
    }
}
