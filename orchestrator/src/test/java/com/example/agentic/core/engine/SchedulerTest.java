package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulerTest {

    @TempDir
    Path dir;

    @Test
    void siblingsRunConcurrentlyAndTheJoinWaitsForAllDependencies() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        Map<String, Set<String>> visibleUpstream = new ConcurrentHashMap<>();
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            visibleUpstream.put(context.nodeId(), context.upstream().keySet());
            if (context.nodeId().equals("b") || context.nodeId().equals("c")) {
                bothRunning.countDown();
                awaitOrFail(bothRunning);
            }
            return new Proposal(Map.of(context.nodeId() + ".txt", context.nodeId()), "wrote " + context.nodeId(),
                    context.upstream().isEmpty() ? List.of("requirement") : List.copyOf(context.upstream().keySet()), Map.of());
        });
        TestEngine engine = new TestEngine().agent(worker);

        try (Orchestrator orchestrator = engine.open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("a", "worker", Set.of(), Autonomy.AUTO, 0, null, "artifact-metadata"),
                    node("b", "worker", Set.of("a"), Autonomy.AUTO, 0, null, "artifact-metadata"),
                    node("c", "worker", Set.of("a"), Autonomy.AUTO, 0, null, "artifact-metadata"),
                    node("d", "worker", Set.of("b", "c"), Autonomy.AUTO, 0, null, "artifact-metadata")), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.COMPLETED);
            List<Event> events = orchestrator.log().events();
            long joinStarted = seq(events, "d", EventType.NODE_STARTED);
            assertThat(joinStarted).isGreaterThan(seq(events, "b", EventType.NODE_DONE))
                    .isGreaterThan(seq(events, "c", EventType.NODE_DONE));
            assertThat(visibleUpstream.get("b")).containsExactly("a");
            assertThat(visibleUpstream.get("d")).containsExactlyInAnyOrder("a", "b", "c");
            for (String id : List.of("a", "b", "c", "d")) {
                assertThat(Files.readString(orchestrator.workspace().root().resolve(id + ".txt"))).isEqualTo(id);
            }
        }
    }

    @Test
    void pausedBranchDoesNotBlockIndependentBranches() throws Exception {
        TestEngine engine = new TestEngine().agent(ScriptedAgent.writing("worker", Map.of()));
        try (Orchestrator orchestrator = engine.open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(
                    node("gated", "worker", Set.of(), Autonomy.APPROVE_AFTER, 0, null, "artifact-metadata"),
                    node("free", "worker", Set.of(), Autonomy.AUTO, 0, null, "artifact-metadata"),
                    node("after_free", "worker", Set.of("free"), Autonomy.AUTO, 0, null, "artifact-metadata"),
                    node("join", "worker", Set.of("gated", "after_free"), Autonomy.AUTO, 0, null)), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.PAUSED);
            assertThat(orchestrator.state().status("gated")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
            assertThat(orchestrator.state().status("after_free")).isEqualTo(NodeStatus.DONE);
            assertThat(orchestrator.state().status("join")).isEqualTo(NodeStatus.PENDING);
        }
    }

    static long seq(List<Event> events, String node, EventType type) {
        return events.stream().filter(event -> node.equals(event.nodeId()) && event.type() == type)
                .mapToLong(Event::seq).findFirst().orElseThrow();
    }

    private static void awaitOrFail(CountDownLatch latch) throws AgentException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AgentException("siblings did not run concurrently");
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new AgentException("interrupted");
        }
    }
}
