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
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property: across 200 seeded random DAGs and failure patterns, no node is ever DONE with an unmet
 * dependency or after a failed exit gate, and every run ends consistently (completed, or safe-stopped with
 * exactly one failed node and the rest DONE or SKIPPED).
 */
class SchedulerPropertyTest {

    @TempDir
    Path dir;

    @Test
    void noNodeIsDoneWithAnUnmetDependencyOrAFailedGate() throws Exception {
        for (int seed = 0; seed < 200; seed++) {
            runScenario(seed);
        }
    }

    private void runScenario(int seed) throws Exception {
        Random random = new Random(seed);
        double failureRate = random.nextDouble() * 0.6;
        Map<String, Integer> failureMessage = new ConcurrentHashMap<>();
        ScriptedAgent.Script script = context -> {
            Random attemptRandom = new Random(seed * 1_000L + context.nodeId().hashCode() * 31L + context.attempt()
                    + (context.fallbackRound() ? 500 : 0));
            boolean fail = attemptRandom.nextDouble() < failureRate;
            int message = failureMessage.merge(context.nodeId(), attemptRandom.nextInt(2), Integer::sum);
            String content = fail ? "FAIL reason-" + (message % 2) : "ok " + context.attempt();
            return new Proposal(Map.of(context.nodeId() + ".txt", content), "work",
                    context.upstream().isEmpty() ? List.of("requirement") : List.copyOf(context.upstream().keySet()), Map.of());
        };
        TestEngine engine = new TestEngine().agent(new ScriptedAgent("worker", script)).agent(new ScriptedAgent("fallback", script));
        WorkflowGraph graph = randomGraph(random);
        Path runDir = dir.resolve("seed-" + seed);

        try (Orchestrator orchestrator = engine.open(runDir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(graph, runDir, RunMetadata.none());
            List<Event> events = orchestrator.log().events();
            assertInvariants(seed, graph, events);
            int stopIndex = events.stream().map(Event::type).toList().indexOf(EventType.SAFE_STOP);
            if (stopIndex >= 0) {
                assertThat(events.subList(stopIndex, events.size())).as("seed %d: nothing starts or settles after SAFE_STOP", seed)
                        .noneMatch(e -> e.type() == EventType.NODE_STARTED || e.type() == EventType.NODE_DONE
                                || e.type() == EventType.NODE_FAILED);
            }
            RunState state = orchestrator.state();
            if (outcome == RunOutcome.COMPLETED) {
                assertThat(state.allDone()).as("seed %d", seed).isTrue();
            } else {
                assertThat(outcome).as("seed %d", seed).isEqualTo(RunOutcome.SAFE_STOPPED);
                assertThat(state.nodes().values().stream().filter(node -> node.status() == NodeStatus.FAILED))
                        .as("seed %d: exactly one failed node; events %s", seed,
                                events.stream().map(e -> e.seq() + ":" + e.nodeId() + ":" + e.type()).toList())
                        .hasSize(1);
                assertThat(state.nodes().values()).allMatch(node -> Set.of(NodeStatus.DONE, NodeStatus.FAILED, NodeStatus.SKIPPED)
                        .contains(node.status()));
            }
        }
    }

    private static WorkflowGraph randomGraph(Random random) {
        int size = 3 + random.nextInt(5);
        List<Node> nodes = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            Set<String> dependencies = new HashSet<>();
            for (int earlier = 0; earlier < index; earlier++) {
                if (random.nextDouble() < 0.4) {
                    dependencies.add("n" + earlier);
                }
            }
            nodes.add(new Node("n" + index, "worker", dependencies, List.of(), List.of("artifact-metadata", "content"),
                    Autonomy.AUTO, random.nextInt(3), random.nextBoolean() ? "fallback" : null, null));
        }
        return new WorkflowGraph("property", "random", "empty", Map.of("maxAgentCalls", 200, "maxTotalAttempts", 200), nodes);
    }

    private static void assertInvariants(int seed, WorkflowGraph graph, List<Event> events) {
        for (int index = 0; index < events.size(); index++) {
            Event event = events.get(index);
            if (event.type() != EventType.NODE_DONE) {
                continue;
            }
            RunState before = RunState.fold(events.subList(0, index));
            for (String dependency : graph.require(event.nodeId()).dependsOn()) {
                assertThat(before.status(dependency)).as("seed %d: %s DONE before dependency %s", seed, event.nodeId(), dependency)
                        .isEqualTo(NodeStatus.DONE);
            }
            int lastCall = lastIndexBefore(events, index, event.nodeId(), EventType.AGENT_CALLED);
            List<Event> attempt = events.subList(lastCall, index).stream()
                    .filter(e -> event.nodeId().equals(e.nodeId())).toList();
            assertThat(attempt).as("seed %d: %s DONE after a failed gate", seed, event.nodeId())
                    .noneMatch(e -> e.type() == EventType.GATE_FAILED || e.type() == EventType.ATTEMPT_DISCARDED);
            assertThat(attempt.stream().filter(e -> e.type() == EventType.GATE_PASSED && event.nodeId().equals(e.nodeId())))
                    .as("seed %d: every exit gate passed for %s", seed, event.nodeId()).hasSize(2);
        }
    }

    private static int lastIndexBefore(List<Event> events, int end, String node, EventType type) {
        for (int index = end - 1; index >= 0; index--) {
            if (events.get(index).type() == type && node.equals(events.get(index).nodeId())) {
                return index;
            }
        }
        throw new AssertionError("no " + type + " for " + node);
    }
}
