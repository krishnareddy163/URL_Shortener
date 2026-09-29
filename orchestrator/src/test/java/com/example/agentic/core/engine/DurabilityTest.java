package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.SqliteEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Event sourcing guarantees: replay equals live state, crash-resume skips DONE nodes, budgets safe-stop. */
class DurabilityTest {

    @TempDir
    Path dir;

    /** Simulates the process dying mid-node (not an agent failure: the engine does not catch Errors). */
    static final class SimulatedCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    private static Node[] pipeline() {
        return new Node[]{
                node("a", "worker", Set.of(), Autonomy.AUTO, 1, null, "artifact-metadata", "content"),
                node("b", "worker", Set.of("a"), Autonomy.APPROVE_AFTER, 1, null, "artifact-metadata"),
                node("c", "worker", Set.of("a"), Autonomy.AUTO, 1, null, "content"),
                node("d", "worker", Set.of("b", "c"), Autonomy.AUTO, 1, null)};
    }

    @Test
    void foldOfPersistedEventsEqualsTheLiveState() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context -> new Proposal(
                Map.of(context.nodeId() + ".txt", context.nodeId().equals("c") && context.attempt() == 1 ? "FAIL once" : "ok"),
                "work", List.of("requirement"), Map.of("n", context.attempt(), "nested", Map.of("list", List.of(1, "two")))));
        TestEngine engine = new TestEngine().agent(worker);
        try (Orchestrator orchestrator = engine.openSqlite(dir)) {
            orchestrator.start(TestEngine.graph(pipeline()), dir, RunMetadata.none());
            orchestrator.approvals().approve("b", "erin", "ok", null);
            orchestrator.resume();

            RunState live = orchestrator.state();
            RunState replayed = RunState.fold(orchestrator.log().events());
            assertThat(replayed).isEqualTo(live);
            assertThat(live.status()).isEqualTo(RunState.RunStatus.COMPLETED);
        }
        try (Orchestrator reopened = engine.openSqlite(dir)) {
            assertThat(reopened.state().status()).isEqualTo(RunState.RunStatus.COMPLETED);
            assertThat(reopened.state()).isEqualTo(RunState.fold(reopened.log().events()));
        }
    }

    @Test
    void killAndResumeDoesNotReExecuteDoneNodes() throws Exception {
        AtomicBoolean crashOnce = new AtomicBoolean(true);
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if (context.nodeId().equals("c") && crashOnce.getAndSet(false)) {
                throw new SimulatedCrash();
            }
            return new Proposal(Map.of(context.nodeId() + ".txt", "ok"), "work", List.of("requirement"), Map.of());
        });
        TestEngine engine = new TestEngine().agent(worker);
        Node[] graph = {
                node("a", "worker", Set.of(), Autonomy.AUTO, 0, null),
                node("b", "worker", Set.of("a"), Autonomy.AUTO, 0, null),
                node("c", "worker", Set.of("b"), Autonomy.AUTO, 0, null)};

        try (Orchestrator first = engine.openSqlite(dir)) {
            WorkflowGraph workflow = TestEngine.graph(graph);
            RunMetadata metadata = RunMetadata.none();
            assertThatThrownBy(() -> first.start(workflow, dir, metadata)).isInstanceOf(SimulatedCrash.class);
            assertThat(first.state().status("c")).isEqualTo(NodeStatus.RUNNING);
        }
        try (Orchestrator second = engine.openSqlite(dir)) {
            assertThat(second.resume()).isEqualTo(RunOutcome.COMPLETED);
        }

        assertThat(worker.calls("a")).isEqualTo(1);
        assertThat(worker.calls("b")).isEqualTo(1);
        assertThat(worker.calls("c")).isEqualTo(2);
        try (SqliteEventStore store = new SqliteEventStore(dir.resolve("events.db"))) {
            assertThat(store.read("test-run").stream().filter(e -> e.type() == EventType.NODE_DONE)).hasSize(3);
        }
    }

    @Test
    void budgetExhaustionSafeStopsSkipsRemainingNodesAndWritesAnIncident() throws Exception {
        ScriptedAgent worker = new ScriptedAgent("worker", context -> new Proposal(
                Map.of("x.txt", context.nodeId().equals("a") ? "ok" : "FAIL attempt " + context.attempt()),
                "work", List.of("requirement"), Map.of()));
        TestEngine engine = new TestEngine().agent(worker).policy(TestEngine.policy(Map.of("maxAgentCalls", 3)));
        try (Orchestrator orchestrator = engine.openSqlite(dir)) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("a", "worker", Set.of(), Autonomy.AUTO, 0, null, "content"),
                    node("b", "worker", Set.of("a"), Autonomy.AUTO, 9, null, "content"),
                    node("c", "worker", Set.of("b"), Autonomy.AUTO, 0, null)), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);
            assertThat(outcome.exitCode()).isEqualTo(20);
            RunState state = orchestrator.state();
            assertThat(state.stopReason()).contains("maxAgentCalls=3");
            assertThat(state.status("a")).isEqualTo(NodeStatus.DONE);
            assertThat(state.status("b")).isEqualTo(NodeStatus.FAILED);
            assertThat(state.status("c")).isEqualTo(NodeStatus.SKIPPED);
            Path incident = dir.resolve("incident.md");
            assertThat(incident).exists();
            assertThat(Files.readString(incident)).contains("Failing node:** `b`").contains("maxAgentCalls=3")
                    .contains("Signature history").contains("| a | DONE |").contains("orchestrator resume");
        }
    }

    @Test
    void wallClockBudgetExcludesTimeSpentWaitingForHumans() throws Exception {
        TestEngine engine = new TestEngine();
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            engine.clock().advance(Duration.ofSeconds(40));
            return new Proposal(Map.of(), "work", List.of("requirement"), Map.of());
        });
        engine.agent(worker).policy(TestEngine.policy(Map.of("maxWallClockSeconds", 100)));
        try (Orchestrator orchestrator = engine.openSqlite(dir)) {
            orchestrator.start(TestEngine.graph(
                    node("gate", "worker", Set.of(), Autonomy.APPROVE_AFTER, 0, null),
                    node("next", "worker", Set.of("gate"), Autonomy.AUTO, 0, null)), dir, RunMetadata.none());
            engine.clock().advance(Duration.ofHours(8));
            orchestrator.approvals().approve("gate", "frank", "after lunch", null);

            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.COMPLETED);
            assertThat(orchestrator.state().activeTime(engine.clock().instant())).isLessThan(Duration.ofSeconds(100));
        }
    }

    @Test
    void safeStoppedRunCanBeResumedAfterTheCauseIsFixed() throws Exception {
        AtomicBoolean broken = new AtomicBoolean(true);
        ScriptedAgent worker = new ScriptedAgent("worker", context -> new Proposal(
                Map.of("x.txt", broken.get() ? "FAIL still broken" : "fixed"), "work", List.of("requirement"), Map.of()));
        TestEngine engine = new TestEngine().agent(worker).policy(TestEngine.policy(Map.of()));
        try (Orchestrator orchestrator = engine.openSqlite(dir)) {
            assertThat(orchestrator.start(TestEngine.graph(node("n", "worker", Set.of(), Autonomy.AUTO, 0, null, "content")),
                    dir, RunMetadata.none())).isEqualTo(RunOutcome.SAFE_STOPPED);
            broken.set(false);

            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.COMPLETED);
            List<Event> events = orchestrator.log().events();
            assertThat(events).extracting(Event::type).contains(EventType.SAFE_STOP, EventType.RESUMED, EventType.RUN_COMPLETED);
        }
    }

}
