package com.example.agentic.agents.live;

import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.UsageMeter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API client. The API key and model come from {@code ANTHROPIC_API_KEY} and
 * {@code ANTHROPIC_MODEL} (no hard-coded model). The key is only ever placed in the request header: it is
 * never logged, never included in exceptions, and never passed to gate subprocesses.
 */
public final class AnthropicClient {
    static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";
    static final int DEFAULT_MAX_TOKENS = 16_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpTransport transport;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicClient(HttpTransport transport, String apiKey, String model, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_MODEL is not set");
        }
        this.transport = transport;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Reads {@code ANTHROPIC_API_KEY}, {@code ANTHROPIC_MODEL} and the optional {@code ANTHROPIC_MAX_TOKENS} (default
     * {@value #DEFAULT_MAX_TOKENS}; file-producing agents return whole files, so the reply budget must be generous).
     */
    public static AnthropicClient fromEnvironment(Map<String, String> environment, HttpTransport transport) {
        String maxTokens = environment.get("ANTHROPIC_MAX_TOKENS");
        int tokens;
        try {
            tokens = maxTokens == null || maxTokens.isBlank() ? DEFAULT_MAX_TOKENS : Integer.parseInt(maxTokens.strip());
        } catch (NumberFormatException _) {
            throw new IllegalStateException("ANTHROPIC_MAX_TOKENS must be a positive integer");
        }
        if (tokens <= 0) {
            throw new IllegalStateException("ANTHROPIC_MAX_TOKENS must be a positive integer");
        }
        return new AnthropicClient(transport, environment.get("ANTHROPIC_API_KEY"), environment.get("ANTHROPIC_MODEL"), tokens);
    }

    public String model() {
        return model;
    }

    /**
     * Sends one system + user turn and returns the concatenated text blocks of the reply. Every answered request,
     * successful or not, is added to {@code usage} with the tokens the API reports and the time it took. A reply
     * cut off at the token limit is an error, because a truncated JSON object cannot be a valid proposal.
     */
    public String complete(String system, String user, UsageMeter usage) throws AgentException {
        String body;
        try {
            body = JSON.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", system,
                    "messages", List.of(Map.of("role", "user", "content", user))));
        } catch (IOException exception) {
            throw new AgentException("cannot encode request", exception);
        }
        HttpTransport.Response response;
        long started = System.nanoTime();
        try {
            response = transport.post(ENDPOINT, Map.of(
                    "x-api-key", apiKey,
                    "anthropic-version", API_VERSION,
                    "content-type", "application/json"), body);
        } catch (IOException exception) {
            throw new AgentException("model call failed: " + exception.getClass().getSimpleName());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new AgentException("model call interrupted");
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        if (response.status() != 200) {
            usage.record(0, 0, elapsed);
            throw new AgentException("model call returned HTTP " + response.status() + errorType(response.body()));
        }
        JsonNode reply;
        try {
            reply = JSON.readTree(response.body());
        } catch (IOException _) {
            usage.record(0, 0, elapsed);
            throw new AgentException("model reply is not valid JSON");
        }
        usage.record(reply.path("usage").path("input_tokens").asLong(0), reply.path("usage").path("output_tokens").asLong(0),
                elapsed);
        if ("max_tokens".equals(reply.path("stop_reason").asText())) {
            throw new AgentException("model reply was cut off at the " + maxTokens + "-token limit (ANTHROPIC_MAX_TOKENS); "
                    + "return fewer or smaller files");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : reply.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new AgentException("model reply contained no text");
        }
        return text.toString();
    }

    private static String errorType(String body) {
        try {
            String type = JSON.readTree(body).path("error").path("type").asText("");
            return type.isEmpty() ? "" : " (" + type + ")";
        } catch (IOException _) {
            return "";
        }
    }
}
