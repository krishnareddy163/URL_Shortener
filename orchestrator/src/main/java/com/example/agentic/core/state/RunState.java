package com.example.agentic.core.state;

import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Run state derived purely by folding the event log ({@link #fold}). Nothing else stores state: the
 * engine applies each event it appends through {@link #apply}, and any process can rebuild an equal
 * value from the persisted log.
 *
 * @param graph           current graph (initial graph plus applied patches); {@code null} before RUN_STARTED
 * @param status          run-level status
 * @param nodes           per-node state keyed by node id
 * @param answers         clarification answers keyed by question id (latest wins)
 * @param questions       every clarification question asked, keyed by id
 * @param attemptCounters agent calls keyed by {@code node|agent|inputHash}
 * @param agentCalls      total agent calls
 * @param totalAttempts   agent calls plus human rejections
 * @param activeMillis    closed active (non-waiting) time
 * @param activeSince     start of the open active segment, or {@code null} while paused or stopped
 * @param stopReason      reason of the last SAFE_STOP, or {@code null}
 * @param lastSeq         sequence number of the last folded event
 */
public record RunState(
        WorkflowGraph graph,
        RunStatus status,
        Map<String, NodeState> nodes,
        Map<String, String> answers,
        Map<String, Question> questions,
        Map<String, Integer> attemptCounters,
        int agentCalls,
        int totalAttempts,
        long activeMillis,
        Instant activeSince,
        String stopReason,
        long lastSeq) {

    private static final Set<NodeStatus> RESETTABLE = Set.of(NodeStatus.RUNNING, NodeStatus.FAILED, NodeStatus.SKIPPED);

    /** Run-level status. */
    public enum RunStatus { NEW, RUNNING, PAUSED, COMPLETED, SAFE_STOPPED }

    /**
     * Per-node state.
     *
     * @param status       lifecycle status
     * @param currentHash  artifact hash of the latest NODE_DONE (kept after invalidation to detect change)
     * @param baseHash     agent output before clarification answers were folded in
     * @param pendingHash  artifact awaiting approval or clarification
     * @param approvedHash artifact hash a human approved (revoked on invalidation)
     * @param feedback     rejection comment to feed into the next attempt
     */
    public record NodeState(NodeStatus status, String currentHash, String baseHash, String pendingHash,
                            String approvedHash, String feedback) {
        static final NodeState INITIAL = new NodeState(NodeStatus.PENDING, null, null, null, null, null);

        NodeState withStatus(NodeStatus newStatus) {
            return new NodeState(newStatus, currentHash, baseHash, pendingHash, approvedHash, feedback);
        }
    }

    /**
     * A clarification question raised by an agent.
     *
     * @param id         question id
     * @param nodeId     node that raised it
     * @param text       the question
     * @param blocking   whether the node waits for an answer
     * @param options    suggested answers
     * @param assumption assumption used if a non-blocking question stays unanswered
     */
    public record Question(String id, String nodeId, String text, boolean blocking, List<String> options,
                           String assumption) {

        public Question {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public RunState {
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        questions = Collections.unmodifiableMap(new LinkedHashMap<>(questions));
        attemptCounters = Collections.unmodifiableMap(new LinkedHashMap<>(attemptCounters));
    }

    public static RunState empty() {
        return new RunState(null, RunStatus.NEW, Map.of(), Map.of(), Map.of(), Map.of(), 0, 0, 0, null, null, 0);
    }

    public static RunState fold(List<Event> events) {
        RunState state = empty();
        for (Event event : events) {
            state = state.apply(event);
        }
        return state;
    }

    public NodeState node(String id) {
        NodeState state = nodes.get(id);
        if (state == null) {
            throw new IllegalArgumentException("unknown node: " + id);
        }
        return state;
    }

    public NodeStatus status(String id) {
        return node(id).status();
    }

    /** Nodes that are PENDING with every dependency DONE, in topological order. */
    public List<Node> readyNodes() {
        if (graph == null || status == RunStatus.SAFE_STOPPED) {
            return List.of();
        }
        return graph.topologicalOrder().stream()
                .map(graph::require)
                .filter(node -> status(node.id()) == NodeStatus.PENDING)
                .filter(node -> node.dependsOn().stream().allMatch(dep -> status(dep) == NodeStatus.DONE))
                .toList();
    }

    public boolean allDone() {
        return !nodes.isEmpty() && nodes.values().stream().allMatch(node -> node.status() == NodeStatus.DONE);
    }

    public boolean anyAwaitingHuman() {
        return nodes.values().stream().anyMatch(node -> node.status() == NodeStatus.AWAITING_APPROVAL
                || node.status() == NodeStatus.AWAITING_CLARIFICATION);
    }

    public int attempts(String nodeId, String agentId, String inputHash) {
        return attemptCounters.getOrDefault(attemptKey(nodeId, agentId, inputHash), 0);
    }

    /** Active (non-waiting) wall-clock time up to {@code now}. */
    public Duration activeTime(Instant now) {
        long open = activeSince == null ? 0 : Math.max(0, Duration.between(activeSince, now).toMillis());
        return Duration.ofMillis(activeMillis + open);
    }

    /** Applies one event; the only way state changes. */
    public RunState apply(Event event) {
        Builder next = new Builder(this);
        next.lastSeq = event.seq();
        String nodeId = event.nodeId();
        switch (event.payload()) {
            case Payload.RunStarted started -> {
                next.graph = started.graph();
                next.nodes.clear();
                next.graph.nodes().forEach(node -> next.nodes.put(node.id(), NodeState.INITIAL));
                next.startSegment(event.ts());
            }
            case Payload.Resumed _ -> {
                next.startSegment(event.ts());
                next.stopReason = null;
                next.nodes.replaceAll((id, node) -> RESETTABLE.contains(node.status()) ? node.withStatus(NodeStatus.PENDING) : node);
            }
            case Payload.NodeStarted _ -> next.updateNode(nodeId, node -> node.withStatus(NodeStatus.RUNNING));
            case Payload.AgentCalled called -> {
                next.attemptCounters.merge(attemptKey(nodeId, called.agent(), event.inputHash()), 1, Integer::sum);
                next.agentCalls++;
                next.totalAttempts++;
            }
            case Payload.NodeFailed _ -> next.updateNode(nodeId, node -> node.withStatus(NodeStatus.FAILED));
            case Payload.ApprovalRequested _ -> next.updateNode(nodeId, node -> new NodeState(NodeStatus.AWAITING_APPROVAL,
                    node.currentHash(), node.baseHash(), event.outputHash(), node.approvedHash(), node.feedback()));
            case Payload.Approved _ -> next.updateNode(nodeId, node -> new NodeState(node.status(), node.currentHash(),
                    node.baseHash(), node.pendingHash(), event.outputHash(), node.feedback()));
            case Payload.Rejected rejected -> {
                next.totalAttempts++;
                next.updateNode(nodeId, node -> new NodeState(NodeStatus.PENDING, node.currentHash(), node.baseHash(),
                        null, null, rejected.comment()));
            }
            case Payload.ClarificationRequested asked -> {
                next.updateNode(nodeId, node -> new NodeState(NodeStatus.AWAITING_CLARIFICATION, node.currentHash(),
                        event.outputHash(), event.outputHash(), node.approvedHash(), node.feedback()));
                next.questions.put(asked.questionId(), new Question(asked.questionId(), nodeId, asked.question(),
                        asked.blocking(), asked.options(), asked.assumptionIfUnanswered()));
            }
            case Payload.Answered answered -> next.answers.put(answered.questionId(), answered.answer());
            case Payload.NodeDone done -> next.updateNode(nodeId, node -> new NodeState(NodeStatus.DONE, event.outputHash(),
                    done.baseHash() != null ? done.baseHash() : event.outputHash(), null, node.approvedHash(), null));
            case Payload.UpstreamRejected rejected ->
                next.updateNode(nodeId, node -> new NodeState(NodeStatus.PENDING, node.currentHash(),
                        node.baseHash(), null, null,
                        "retries exhausted in downstream node '" + rejected.downstream() + "': " + rejected.feedback()));
            case Payload.Invalidated _ -> {
                next.updateNode(nodeId, node -> new NodeState(NodeStatus.PENDING, node.currentHash(),
                        node.baseHash(), null, null, node.feedback()));
                if (next.status == RunStatus.COMPLETED) {
                    next.status = RunStatus.PAUSED;
                }
            }
            case Payload.Replan replan -> {
                if (replan.patch() != null) {
                    next.graph = replan.patch().applyTo(next.graph);
                    next.graph.nodes().forEach(node -> next.nodes.putIfAbsent(node.id(), NodeState.INITIAL));
                }
            }
            case Payload.RunPaused _ -> {
                next.closeSegment(event.ts());
                next.status = RunStatus.PAUSED;
            }
            case Payload.SafeStopped stopped -> {
                next.closeSegment(event.ts());
                next.status = RunStatus.SAFE_STOPPED;
                next.stopReason = stopped.reason();
                next.nodes.replaceAll((id, node) -> node.status() == NodeStatus.DONE || node.status() == NodeStatus.FAILED
                        ? node : node.withStatus(NodeStatus.SKIPPED));
            }
            case Payload.RunCompleted _ -> {
                next.closeSegment(event.ts());
                next.status = RunStatus.COMPLETED;
            }
            case Payload.GatePassed _, Payload.GateFailed _, Payload.AttemptDiscarded _, Payload.Fallback _ -> {
                // Audit facts only: they change no derived state beyond the sequence number.
            }
        }
        return next.build();
    }

    static String attemptKey(String nodeId, String agentId, String inputHash) {
        return nodeId + "|" + agentId + "|" + inputHash;
    }

    private static final class Builder {
        private WorkflowGraph graph;
        private RunStatus status;
        private final Map<String, NodeState> nodes;
        private final Map<String, String> answers;
        private final Map<String, Question> questions;
        private final Map<String, Integer> attemptCounters;
        private int agentCalls;
        private int totalAttempts;
        private long activeMillis;
        private Instant activeSince;
        private String stopReason;
        private long lastSeq;

        private Builder(RunState state) {
            graph = state.graph;
            status = state.status;
            nodes = new LinkedHashMap<>(state.nodes);
            answers = new LinkedHashMap<>(state.answers);
            questions = new LinkedHashMap<>(state.questions);
            attemptCounters = new LinkedHashMap<>(state.attemptCounters);
            agentCalls = state.agentCalls;
            totalAttempts = state.totalAttempts;
            activeMillis = state.activeMillis;
            activeSince = state.activeSince;
            stopReason = state.stopReason;
            lastSeq = state.lastSeq;
        }

        private void updateNode(String nodeId, UnaryOperator<NodeState> change) {
            NodeState current = nodes.get(nodeId);
            if (current == null) {
                throw new IllegalStateException("event references unknown node " + nodeId);
            }
            nodes.put(nodeId, change.apply(current));
        }

        private void startSegment(Instant ts) {
            status = RunStatus.RUNNING;
            if (activeSince == null) {
                activeSince = ts;
            }
        }

        private void closeSegment(Instant ts) {
            if (activeSince != null) {
                activeMillis += Math.max(0, Duration.between(activeSince, ts).toMillis());
                activeSince = null;
            }
        }

        private RunState build() {
            return new RunState(graph, status, nodes, answers, questions, attemptCounters, agentCalls, totalAttempts,
                    activeMillis, activeSince, stopReason, lastSeq);
        }
    }
}
