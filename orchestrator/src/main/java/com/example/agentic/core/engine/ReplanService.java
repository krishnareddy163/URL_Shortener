package com.example.agentic.core.engine;

import com.example.agentic.core.graph.GraphPatch;
import com.example.agentic.core.graph.GraphValidator;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.workspace.FileTrees;
import com.example.agentic.core.workspace.PromotionConflictException;
import com.example.agentic.core.workspace.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Dynamic re-planning, both mechanisms:
 * <ol>
 *   <li><b>Hash invalidation</b>: when a node's artifact hash changes, every non-PENDING downstream node is
 *   invalidated (its promoted files reverted, pending work discarded, approvals revoked) and reset to PENDING.</li>
 *   <li><b>Graph patch</b>: an agent-proposed structural change is validated (acyclic, known gates/agents,
 *   no settled node's dependencies altered) and recorded as a REPLAN event that {@code RunState} folds in.</li>
 * </ol>
 */
public final class ReplanService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<NodeStatus> SETTLED = Set.of(NodeStatus.DONE, NodeStatus.RUNNING,
            NodeStatus.AWAITING_APPROVAL, NodeStatus.AWAITING_CLARIFICATION);

    private final RunLog log;
    private final Workspace workspace;
    private final RunPaths paths;
    private final GraphValidator validator;

    public ReplanService(RunLog log, Workspace workspace, RunPaths paths, GraphValidator validator) {
        this.log = log;
        this.workspace = workspace;
        this.paths = paths;
        this.validator = validator;
    }

    /**
     * A proposal's {@code data.graphPatch}, parsed.
     *
     * @param patch the patch, if present and well-formed
     * @param error why it could not be parsed, if malformed
     */
    public record PatchParse(Optional<GraphPatch> patch, Optional<String> error) {
    }

    /** Parses {@code data.graphPatch}; malformed agent output is reported, never thrown. */
    public static PatchParse patchIn(Map<String, Object> data) {
        Object raw = data.get("graphPatch");
        if (raw == null) {
            return new PatchParse(Optional.empty(), Optional.empty());
        }
        try {
            return new PatchParse(Optional.of(JSON.convertValue(raw, GraphPatch.class)), Optional.empty());
        } catch (IllegalArgumentException exception) {
            return new PatchParse(Optional.empty(), Optional.of("graphPatch is malformed: " + exception.getMessage()));
        }
    }

    /** All reasons the patch is unacceptable in the current state; empty if acceptable. */
    public List<String> validate(GraphPatch patch, RunState state) {
        List<String> errors = new ArrayList<>();
        WorkflowGraph current = state.graph();
        for (Node added : patch.addNodes()) {
            current.node(added.id()).filter(existing -> !existing.equals(added))
                    .ifPresent(existing -> errors.add("patch redefines existing node '" + added.id() + "'"));
            if (added.task() != null) {
                errors.add("patch sets a task for node '" + added.id() + "'; tasks come only from the workflow file");
            }
        }
        WorkflowGraph patched = patch.applyTo(current);
        List<GraphPatch.Edge> edges = new ArrayList<>(patch.addEdges());
        edges.addAll(patch.removeEdges());
        for (GraphPatch.Edge edge : edges) {
            if (patched.node(edge.from()).isEmpty() || patched.node(edge.to()).isEmpty()) {
                errors.add("patch edge references unknown node: " + edge.from() + " -> " + edge.to());
            }
        }
        for (Node node : current.nodes()) {
            NodeStatus status = state.status(node.id());
            if (SETTLED.contains(status) && !patched.require(node.id()).dependsOn().equals(node.dependsOn())) {
                errors.add("patch alters dependencies of " + status + " node '" + node.id() + "'");
            }
        }
        errors.addAll(validator.validate(patched));
        return errors;
    }

    /** Validates and records a graph patch proposed by {@code sourceNode}. */
    public void apply(GraphPatch patch, String sourceNode) throws WorkflowValidationException {
        List<String> errors = validate(patch, log.state());
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
        String reason = patch.reason() == null ? "graph patch from " + sourceNode : patch.reason();
        log.append(sourceNode, Payload.Replan.ofGraphPatch(reason, patch, patch.addNodes().stream().map(Node::id).toList()),
                sourceNode, null, null);
    }

    /**
     * Invalidates everything downstream of {@code nodeId} after its artifact changed from {@code oldHash} to
     * {@code newHash}. Promoted files of invalidated DONE nodes are reverted as one validated batch (newest
     * promotion first); on a conflict nothing is reverted and no event is written.
     *
     * @return invalidated node ids (empty if nothing needed invalidation)
     */
    public List<String> invalidateDownstream(String nodeId, String oldHash, String newHash, String reason)
            throws IOException, PromotionConflictException {
        if (oldHash == null || Hashing.sameHash(oldHash, newHash)) {
            return List.of();
        }
        RunState state = log.state();
        List<String> targets = state.graph().downstreamOf(nodeId).stream()
                .filter(id -> state.status(id) != NodeStatus.PENDING)
                .toList();
        if (targets.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> reverted = revertPromotions(state, targets);
        List<String> revertedFiles = reverted.values().stream().flatMap(List::stream).distinct().toList();
        workspace.history().commit(revertedFiles, "Revert " + String.join(", ", targets) + " after " + nodeId + " changed",
                reason + "\n\nInvalidated-by: " + nodeId + "\nRun: " + log.runId(), "agentic-sdlc engine");
        for (String target : targets) {
            FileTrees.deleteTree(paths.pending(target));
            RunState.NodeState node = state.node(target);
            log.append(target, new Payload.Invalidated(reason, nodeId, oldHash, newHash, node.status().name(),
                    node.approvedHash() != null, reverted.getOrDefault(target, List.of())), RunLog.ENGINE, null,
                    node.currentHash());
        }
        log.append(nodeId, Payload.Replan.ofInvalidation(reason, nodeId, oldHash, newHash, targets), RunLog.ENGINE, null, newHash);
        return targets;
    }

    /**
     * Resets {@code upstreamNodeId} to PENDING with {@code feedback}, invalidates everything downstream of it
     * (including {@code downstreamNodeId}), reverts all promoted files, and emits a REPLAN summary. Called when
     * a downstream node exhausts its retries and has {@code rejectUpstream} configured instead of safe-stopping.
     */
    public void rejectUpstream(String downstreamNodeId, String upstreamNodeId, String feedback)
            throws IOException, PromotionConflictException {
        RunState state = log.state();
        String upstreamHash = state.node(upstreamNodeId).currentHash();

        // Every node downstream of the upstream (including the failing node) that isn't already PENDING.
        List<String> cascade = state.graph().downstreamOf(upstreamNodeId).stream()
                .filter(id -> state.status(id) != NodeStatus.PENDING)
                .toList();

        // Revert files promoted by the upstream and any settled descendants.
        List<String> toRevert = new ArrayList<>();
        if (state.status(upstreamNodeId) == NodeStatus.DONE) {
            toRevert.add(upstreamNodeId);
        }
        cascade.stream().filter(id -> state.status(id) == NodeStatus.DONE).forEach(toRevert::add);
        Map<String, List<String>> reverted = revertPromotions(state, toRevert);

        if (!reverted.isEmpty()) {
            List<String> allFiles = reverted.values().stream().flatMap(List::stream).distinct().toList();
            workspace.history().commit(allFiles,
                    "Revert for upstream rejection: " + upstreamNodeId,
                    "Node '" + downstreamNodeId + "' exhausted retries; resetting '" + upstreamNodeId + "' with feedback.\n\nRun: " + log.runId(),
                    "agentic-sdlc engine");
        }

        // Reset the upstream node to PENDING with feedback.
        log.append(upstreamNodeId, new Payload.UpstreamRejected(downstreamNodeId, feedback),
                RunLog.ENGINE, null, upstreamHash);

        // Reset each downstream node to PENDING (preserving existing feedback).
        for (String target : cascade) {
            RunState.NodeState ns = state.node(target);
            log.append(target, new Payload.Invalidated("upstream-rejection of " + upstreamNodeId, upstreamNodeId,
                    upstreamHash, null, ns.status().name(), ns.approvedHash() != null,
                    reverted.getOrDefault(target, List.of())), RunLog.ENGINE, null, ns.currentHash());
        }

        // Emit a REPLAN summary so the report and metrics capture the event.
        List<String> allReset = new ArrayList<>();
        allReset.add(upstreamNodeId);
        allReset.addAll(cascade);
        log.append(downstreamNodeId, Payload.Replan.ofInvalidation(
                "upstream rejection: '" + downstreamNodeId + "' retries exhausted, resetting '" + upstreamNodeId + "'",
                upstreamNodeId, upstreamHash, null, allReset), RunLog.ENGINE, null, null);
    }

    private Map<String, List<String>> revertPromotions(RunState state, List<String> targets)
            throws IOException, PromotionConflictException {
        Map<String, Long> lastDoneSeq = new HashMap<>();
        for (Event event : log.events()) {
            if (event.type() == EventType.NODE_DONE) {
                lastDoneSeq.put(event.nodeId(), event.seq());
            }
        }
        List<String> newestFirst = targets.stream()
                .filter(id -> state.status(id) == NodeStatus.DONE)
                .sorted(Comparator.comparingLong((String id) -> lastDoneSeq.getOrDefault(id, 0L)).reversed())
                .toList();
        Map<Path, String> nodeByRecord = new HashMap<>();
        List<Path> records = new ArrayList<>();
        for (String target : newestFirst) {
            Path recordDir = paths.promotion(target, state.node(target).currentHash());
            nodeByRecord.put(recordDir, target);
            records.add(recordDir);
        }
        Map<String, List<String>> reverted = new HashMap<>();
        workspace.revertAll(records).forEach((recordDir, files) -> reverted.put(nodeByRecord.get(recordDir), files));
        return reverted;
    }
}
