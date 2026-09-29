package com.example.agentic.core.state;

import com.example.agentic.core.graph.GraphPatch;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadTest {

    private static Event event(Payload payload) {
        return new Event(1, "run", "node", payload.type(), "engine", null, null, Instant.EPOCH, payload.toDetails());
    }

    @Test
    void everyPayloadSurvivesTheStoredJsonForm() {
        List<Payload> samples = List.of(
                new Payload.Resumed("PAUSED"),
                new Payload.NodeStarted("developer", "default", List.of("design")),
                new Payload.AgentCalled("developer", 2, true, "p", "r", null, Map.of("model", "m"), new ModelUsage(1, 10, 20, 300)),
                new Payload.AgentCalled("developer", 1, false, "p", null, "boom", Map.of(), null),
                new Payload.GatePassed("unit-tests", "exit", null),
                new Payload.GatePassed("test-coverage", "exit", "line 99.0%, branch 100.0%"),
                new Payload.GateFailed("unit-tests", "exit", "boom", "sig"),
                new Payload.AttemptDiscarded("developer", 1, "unit-tests", "sig", "boom"),
                new Payload.Fallback("live", "mock", "breaker"),
                new Payload.NodeFailed("developer", "unit-tests", "sig", "boom", true),
                new Payload.ApprovalRequested("s", "APPROVE_AFTER", List.of("why"), List.of("a"), "b",
                        Map.of("a", "h"), "1 file", "diff"),
                new Payload.Approved("carol", "ok", "h"),
                new Payload.Rejected("carol", "no", "h"),
                new Payload.ClarificationRequested("q", "which?", true, List.of("x", "y"), "x", Map.of(), List.of(), ""),
                new Payload.Answered("q", "x", null),
                new Payload.Invalidated("changed", "req", "o", "n", "DONE", true, List.of("a")),
                Payload.Replan.ofGraphPatch("needs migration", new GraphPatch(List.of(), List.of(), List.of(), "r"),
                        List.of("migration")),
                Payload.Replan.ofInvalidation("changed", "req", "o", "n", List.of("design")),
                new Payload.NodeDone(List.of("a"), "b", "carol", "0123456789abcdef0123456789abcdef01234567"),
                new Payload.NodeDone(List.of(), "b", null, null),
                new Payload.RunPaused(Map.of("release", "AWAITING_APPROVAL")),
                new Payload.SafeStopped("boom", "node", List.of("a")),
                new Payload.RunCompleted(3));

        assertThat(samples).allSatisfy(payload -> assertThat(event(payload).payload()).isEqualTo(payload));
        assertThat(samples.stream().map(Payload::type).distinct().count() + 1)
                .as("every event type but RUN_STARTED is sampled here; RUN_STARTED is covered by every run")
                .isEqualTo(Arrays.stream(EventType.values()).count());
    }

    @Test
    void decodingRejectsUnknownFieldsAndTheWrongPayloadType() {
        Event misspelled = new Event(1, "run", "node", EventType.GATE_PASSED, "engine", null, null, Instant.EPOCH,
                Map.of("gaet", "unit-tests"));
        assertThatThrownBy(misspelled::payload).isInstanceOf(IllegalArgumentException.class);

        Event passed = event(new Payload.GatePassed("unit-tests", "exit", null));
        assertThatThrownBy(() -> passed.payload(Payload.GateFailed.class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivedAccessorsAreNotStored() {
        assertThat(Payload.Replan.ofInvalidation("r", "u", "o", "n", List.of()).toDetails()).doesNotContainKey("invalidation");
    }
}
