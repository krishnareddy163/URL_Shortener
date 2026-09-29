package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;

/** Retries, circuit breaker, fallback, rollback and path-guard behavior of the node runner. */
class RecoveryTest {

    @TempDir
    Path dir;

    @Test
    void retriesAreBoundedByMaxRetries() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context ->
                proposal(Map.of("out.txt", "FAIL distinct failure " + context.attempt())));
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 2, null, "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);
            assertThat(outcome.exitCode()).isEqualTo(20);
            assertThat(worker.calls("n")).isEqualTo(3);
            assertThat(count(orchestrator, EventType.ATTEMPT_DISCARDED)).isEqualTo(3);
            assertThat(orchestrator.state().status("n")).isEqualTo(NodeStatus.FAILED);
            assertThat(lastOf(orchestrator, EventType.NODE_FAILED).payload(Payload.NodeFailed.class).breakerTripped()).isFalse();
        }
    }

    @Test
    void circuitBreakerTripsOnTwoIdenticalSignatures() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context -> proposal(Map.of("out.txt", "FAIL same every time")));
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 5, null, "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);
            assertThat(worker.calls("n")).as("breaker stops well before maxRetries=5").isEqualTo(2);
            assertThat(lastOf(orchestrator, EventType.NODE_FAILED).payload(Payload.NodeFailed.class).breakerTripped()).isTrue();
        }
    }

    @Test
    void fallbackAgentGetsExactlyOneRound() throws Exception {
        ScriptedAgent primary = new ScriptedAgent("worker", context -> proposal(Map.of("out.txt", "FAIL primary")));
        ScriptedAgent fallback = new ScriptedAgent("fallback", context -> proposal(Map.of("out.txt", "FAIL fallback " + context.attempt())));
        try (Orchestrator orchestrator = new TestEngine().agent(primary).agent(fallback).open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 1, "fallback", "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);
            assertThat(primary.calls("n")).isEqualTo(2);
            assertThat(fallback.calls("n")).isEqualTo(2);
            assertThat(count(orchestrator, EventType.FALLBACK)).isEqualTo(1);
            assertThat(lastOf(orchestrator, EventType.FALLBACK).payload(Payload.Fallback.class).to()).isEqualTo("fallback");
        }
    }

    @Test
    void fallbackCanRecoverTheNode() throws Exception {
        ScriptedAgent primary = new ScriptedAgent("worker", context -> proposal(Map.of("out.txt", "FAIL primary")));
        ScriptedAgent fallback = new ScriptedAgent("fallback", context -> proposal(Map.of("out.txt", "fixed by fallback")));
        try (Orchestrator orchestrator = new TestEngine().agent(primary).agent(fallback).open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 3, "fallback", "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.COMPLETED);
            assertThat(Files.readString(orchestrator.workspace().root().resolve("out.txt"))).isEqualTo("fixed by fallback");
        }
    }

    @Test
    void retryReceivesTheGateFailureAsFeedback() throws Exception {
        AtomicReference<String> feedbackSeen = new AtomicReference<>();
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if (context.attempt() == 1) {
                return proposal(Map.of("out.txt", "FAIL expected 7 but was 8"));
            }
            feedbackSeen.set(context.feedback());
            return proposal(Map.of("out.txt", "fixed"));
        });
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            assertThat(orchestrator.start(TestEngine.graph(
                    node("n", "worker", Set.of(), Autonomy.AUTO, 2, null, "content")), dir, RunMetadata.none())).isEqualTo(RunOutcome.COMPLETED);

            assertThat(feedbackSeen.get()).contains("expected 7 but was 8");
        }
    }

    @Test
    void failedAttemptLeavesThePromotedWorkspaceByteIdentical() throws Exception {
        AtomicReference<Orchestrator> holder = new AtomicReference<>();
        AtomicReference<String> hashBeforeFailure = new AtomicReference<>();
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if (context.nodeId().equals("first")) {
                return proposal(Map.of("src/App.java", "class App {}\n", "README.md", "v1\n"));
            }
            try {
                hashBeforeFailure.compareAndSet(null, holder.get().workspace().treeHash());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return proposal(Map.of("src/App.java", "class App { FAIL broken }\n", "src/New.java", "class New {}\n"));
        });
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            holder.set(orchestrator);
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("first", "worker", Set.of(), Autonomy.AUTO, 0, null, "content"),
                    node("second", "worker", Set.of("first"), Autonomy.AUTO, 1, null, "content")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);
            assertThat(orchestrator.workspace().treeHash()).isEqualTo(hashBeforeFailure.get());
            assertThat(Files.readString(orchestrator.workspace().root().resolve("src/App.java"))).isEqualTo("class App {}\n");
            assertThat(Files.exists(orchestrator.workspace().root().resolve("src/New.java"))).isFalse();
            assertThat(orchestrator.state().status("first")).isEqualTo(NodeStatus.DONE);
        }
    }

    @Test
    void outOfScopeWritesAreRejectedByThePathGuard() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context -> proposal(Map.of("../escape.txt", "x")));
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            orchestrator.start(TestEngine.graph(node("n", "worker", Set.of(), Autonomy.AUTO, 0, null)), dir, RunMetadata.none());

            Event failure = lastOf(orchestrator, EventType.GATE_FAILED);
            assertThat(failure.payload(Payload.GateFailed.class).gate()).isEqualTo("path-allowlist");
            assertThat(failure.payload(Payload.GateFailed.class).reason()).contains("traversal");
            assertThat(Files.exists(dir.resolve("escape.txt"))).isFalse();
        }
    }

    @Test
    void agentExceptionsCountAsFailedAttempts() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            throw new AgentException("no fixture for attempt " + context.attempt());
        });
        try (Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore())) {
            orchestrator.start(TestEngine.graph(node("n", "worker", Set.of(), Autonomy.AUTO, 1, null)), dir, RunMetadata.none());

            assertThat(worker.calls("n")).isEqualTo(2);
            assertThat(orchestrator.log().events()).filteredOn(event -> event.type() == EventType.AGENT_CALLED)
                    .allSatisfy(event -> assertThat(event.payload(Payload.AgentCalled.class).error()).startsWith("no fixture for attempt"));
        }
    }

    static Proposal proposal(Map<String, String> files) {
        return new Proposal(files, "scripted", List.of("requirement"), Map.of());
    }

    static long count(Orchestrator orchestrator, EventType type) {
        return orchestrator.log().events().stream().filter(event -> event.type() == type).count();
    }

    static Event lastOf(Orchestrator orchestrator, EventType type) {
        return orchestrator.log().events().stream().filter(event -> event.type() == type).reduce((a, b) -> b).orElseThrow();
    }
}
