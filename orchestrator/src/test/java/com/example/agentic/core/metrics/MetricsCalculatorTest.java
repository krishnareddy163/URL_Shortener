package com.example.agentic.core.metrics;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Hand-built event logs with known timestamps yield exact metric values. */
class MetricsCalculatorTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final List<Event> events = new ArrayList<>();

    private void add(int second, String node, EventType type, String outputHash, Map<String, Object> details) {
        events.add(new Event(events.size() + 1, "r", node, type, "engine", "in", outputHash, T0.plusSeconds(second), details));
    }

    private static Map<String, Object> graph(String... ids) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String id : ids) {
            nodes.add(Map.of("id", id, "agent", "worker"));
        }
        return Map.of("graph", Map.of("name", "m", "requirement", "r", "nodes", nodes));
    }

    @Test
    void computesRatesRetriesMttrAndNetVersusGrossLatency() {
        add(0, null, EventType.RUN_STARTED, null, graph("a", "b"));
        add(1, "a", EventType.NODE_STARTED, null, Map.of());
        add(1, "a", EventType.AGENT_CALLED, null, Map.of("agent", "worker"));
        add(2, "a", EventType.GATE_FAILED, null, Map.of("gate", "unit-tests"));
        add(2, "a", EventType.ATTEMPT_DISCARDED, null, Map.of());
        add(3, "a", EventType.AGENT_CALLED, null, Map.of("agent", "worker"));
        add(4, "a", EventType.GATE_PASSED, null, Map.of("gate", "unit-tests"));
        add(5, "a", EventType.NODE_DONE, "ha", Map.of());
        add(6, "b", EventType.NODE_STARTED, null, Map.of());
        add(6, "b", EventType.AGENT_CALLED, null, Map.of("agent", "worker"));
        add(6, "b", EventType.APPROVAL_REQUESTED, "hb", Map.of());
        add(6, null, EventType.RUN_PAUSED, null, Map.of());
        add(16, "b", EventType.APPROVED, "hb", Map.of("by", "x"));
        add(17, null, EventType.RESUMED, null, Map.of());
        add(17, "b", EventType.NODE_DONE, "hb", Map.of());
        add(20, null, EventType.RUN_COMPLETED, null, Map.of());

        RunMetrics metrics = MetricsCalculator.calculate(events);
        assertThat(metrics.toMarkdown()).as("no model usage row in a MOCK run").doesNotContain("Model calls");

        assertThat(metrics.totalNodes()).isEqualTo(2);
        assertThat(metrics.firstPassNodes()).isEqualTo(1);
        assertThat(metrics.successRate()).isEqualTo(0.5);
        assertThat(metrics.retries()).isEqualTo(1);
        assertThat(metrics.rollbacks()).isEqualTo(1);
        assertThat(metrics.retriesPerNode()).isEqualTo(Map.of("a", 1));
        assertThat(metrics.fallbacks()).isZero();
        assertThat(metrics.mttr()).contains(Duration.ofSeconds(3));
        assertThat(metrics.grossLatency()).contains(Duration.ofSeconds(20));
        assertThat(metrics.humanWait()).isEqualTo(Duration.ofSeconds(10));
        assertThat(metrics.netLatency()).contains(Duration.ofSeconds(10));
        assertThat(metrics.approvalsRequested()).isEqualTo(1);
        assertThat(metrics.approvalsGranted()).isEqualTo(1);
        assertThat(metrics.approvalsRejected()).isZero();
        assertThat(metrics.gateFailuresByGate()).isEqualTo(Map.of("unit-tests", 1));
        assertThat(metrics.toMarkdown()).contains("| Success rate (first-pass DONE / nodes) | 50.0% (1/2) |")
                .contains("| End-to-end latency (net) | 10.000 s |");
    }

    @Test
    void overlappingWaitsAreCountedOnceAndPostCompletionIdleIsExcluded() {
        add(0, null, EventType.RUN_STARTED, null, graph("a", "b", "q"));
        add(0, "q", EventType.CLARIFICATION_REQUESTED, "hq", Map.of("questionId", "q-1"));
        add(4, "q", EventType.ANSWERED, null, Map.of("questionId", "q-1"));
        add(5, "q", EventType.NODE_DONE, "hq", Map.of());
        add(6, "a", EventType.APPROVAL_REQUESTED, "h1", Map.of());
        add(8, "b", EventType.APPROVAL_REQUESTED, "h2", Map.of());
        add(12, "a", EventType.APPROVED, "h1", Map.of());
        add(14, "b", EventType.REJECTED, "h2", Map.of());
        add(15, "b", EventType.APPROVAL_REQUESTED, "h3", Map.of());
        add(16, "b", EventType.APPROVED, "h3", Map.of());
        add(16, "a", EventType.NODE_DONE, "h1", Map.of());
        add(16, "b", EventType.NODE_DONE, "h3", Map.of());
        add(20, null, EventType.RUN_COMPLETED, null, Map.of());
        add(100, "q", EventType.ANSWERED, null, Map.of("questionId", "q-1"));
        add(110, null, EventType.RESUMED, null, Map.of());
        add(115, null, EventType.RUN_COMPLETED, null, Map.of());

        RunMetrics metrics = MetricsCalculator.calculate(events);

        assertThat(metrics.grossLatency()).contains(Duration.ofSeconds(115));
        assertThat(metrics.humanWait()).as("4 (clarification) + 8 (approvals 6..14) + 1 (15..16) + 90 (idle 20..110)")
                .isEqualTo(Duration.ofSeconds(103));
        assertThat(metrics.netLatency()).contains(Duration.ofSeconds(12));
        assertThat(metrics.approvalsRequested()).isEqualTo(3);
        assertThat(metrics.approvalsRejected()).isEqualTo(1);
        assertThat(metrics.clarifications()).isEqualTo(1);
        assertThat(metrics.mttr()).isEmpty();
    }

    @Test
    void incompleteRunHasNoLatency() {
        add(0, null, EventType.RUN_STARTED, null, graph("a"));
        add(3, null, EventType.SAFE_STOP, null, Map.of("reason", "x"));

        RunMetrics metrics = MetricsCalculator.calculate(events);

        assertThat(metrics.grossLatency()).isEmpty();
        assertThat(metrics.netLatency()).isEmpty();
        assertThat(metrics.successRate()).isZero();
    }
}
