package com.example.agentic.agents.live;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.metrics.MetricsCalculator;
import com.example.agentic.core.metrics.RunMetrics;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.ModelUsage;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;

/** A live file-producing agent inside the real engine: its output is gated like any agent's, then falls back. */
class LiveModeEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    @Test
    void liveOutputThatFailsGatesIsRolledBackThenTheFixtureFallbackCompletesTheNode() throws Exception {
        String reply = JSON.writeValueAsString(Map.of("content", List.of(Map.of("type", "text", "text",
                "{\"rationale\":\"first try\",\"derivedFrom\":[\"requirement\"],"
                        + "\"files\":{\"out.txt\":\"" + TestEngine.FAIL_MARKER + " model output\"}}")),
                "usage", Map.of("input_tokens", 120, "output_tokens", 80)));
        LiveModeFixtures.CountingTransport transport = new LiveModeFixtures.CountingTransport(reply);
        LiveBuilderAgent live = new LiveBuilderAgent("worker", "Write out.txt.", List.of("**"),
                new AnthropicClient(transport, "k", "m", 100));
        ScriptedAgent fallback = new ScriptedAgent("fallback", context -> new Proposal(Map.of("out.txt", "fixture output"),
                "fixture", List.of("requirement"), Map.of()));

        try (Orchestrator orchestrator = new TestEngine().agent(live).agent(fallback).open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 1, "fallback", "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.COMPLETED);
            assertThat(transport.calls()).as("maxRetries=1: two live attempts").isEqualTo(2);
            assertThat(orchestrator.log().events()).filteredOn(event -> event.type() == EventType.AGENT_CALLED)
                    .extracting(event -> event.payload(Payload.AgentCalled.class).agentMetadata().get("mode"))
                    .containsExactly("LIVE", "LIVE", null);
            assertThat(orchestrator.log().events()).filteredOn(event -> event.type() == EventType.AGENT_CALLED)
                    .extracting(event -> event.payload(Payload.AgentCalled.class).usage())
                    .as("rolled-back live attempts still record what they cost; the fixture fallback records nothing")
                    .extracting(usage -> usage == null ? null : List.of(usage.calls(), usage.inputTokens(), usage.outputTokens()))
                    .containsExactly(List.of(1, 120L, 80L), List.of(1, 120L, 80L), null);
            RunMetrics metrics = MetricsCalculator.calculate(orchestrator.log().events());
            assertThat(metrics.modelUsage()).extracting(ModelUsage::calls, ModelUsage::inputTokens, ModelUsage::outputTokens)
                    .containsExactly(2, 240L, 160L);
            assertThat(metrics.toMarkdown()).contains("| Model calls / input tokens / output tokens / model time | 2 / 240 / 160 / ");
            assertThat(orchestrator.log().events()).filteredOn(event -> event.type() == EventType.ATTEMPT_DISCARDED).hasSize(2);
            assertThat(orchestrator.log().events()).anyMatch(event -> event.type() == EventType.FALLBACK);
            assertThat(Files.readString(orchestrator.workspace().root().resolve("out.txt"))).isEqualTo("fixture output");
        }
    }
}
