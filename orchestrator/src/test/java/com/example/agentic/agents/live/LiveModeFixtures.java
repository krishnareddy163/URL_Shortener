package com.example.agentic.agents.live;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Test doubles for live agents. */
final class LiveModeFixtures {

    private LiveModeFixtures() {
    }

    /** Replies with the same body every time and counts calls. */
    static final class CountingTransport implements HttpTransport {
        private final String reply;
        private final AtomicInteger calls = new AtomicInteger();

        CountingTransport(String reply) {
            this.reply = reply;
        }

        @Override
        public Response post(URI uri, Map<String, String> headers, String body) {
            calls.incrementAndGet();
            return new Response(200, reply);
        }

        int calls() {
            return calls.get();
        }
    }
}
