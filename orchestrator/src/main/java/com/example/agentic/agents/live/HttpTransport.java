package com.example.agentic.agents.live;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** Minimal HTTP POST abstraction so the LLM client can be tested without a network. */
public interface HttpTransport {

    Response post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException;

    /**
     * An HTTP response.
     *
     * @param status status code
     * @param body   response body
     */
    record Response(int status, String body) {
    }
}
