package com.example.agentic.core.approval;

import com.example.agentic.core.engine.NodeCompletion;
import com.example.agentic.core.engine.RunPaths;
import com.example.agentic.core.engine.SafeStop;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ArtifactStore;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Human approval checkpoints (R5). An approval is bound to the artifact hash in the latest
 * {@code APPROVAL_REQUESTED}: the retained staged files are re-hashed and must still match, and an
 * approver-supplied hash must match too. Otherwise {@link StaleApprovalException} and nothing changes.
 */
public final class ApprovalService {
    private final RunLog log;
    private final ArtifactStore artifacts;
    private final RunPaths paths;
    private final NodeCompletion completion;
    private final SafeStop safeStop;

    public ApprovalService(RunLog log, ArtifactStore artifacts, RunPaths paths, NodeCompletion completion, SafeStop safeStop) {
        this.log = log;
        this.artifacts = artifacts;
        this.paths = paths;
        this.completion = completion;
        this.safeStop = safeStop;
    }

    /**
     * What a reviewer sees for a node awaiting approval.
     *
     * @param nodeId   node
     * @param hash     artifact hash being approved
     * @param summary  agent rationale
     * @param reasons  why approval is required (autonomy level and risk flags)
     * @param files    files that will be promoted
     * @param diffStat per-file change counts
     * @param diff     unified diff against the staging base
     */
    public record PendingApproval(String nodeId, String hash, String summary, List<String> reasons, List<String> files,
                                  String diffStat, String diff) {

        public PendingApproval {
            reasons = List.copyOf(reasons);
            files = List.copyOf(files);
        }
    }

    public List<PendingApproval> pending() {
        RunState state = log.state();
        List<Event> events = log.events();
        List<PendingApproval> pending = new ArrayList<>();
        state.nodes().forEach((nodeId, node) -> {
            if (node.status() == NodeStatus.AWAITING_APPROVAL) {
                HumanDecisions.latest(events, nodeId, EventType.APPROVAL_REQUESTED).ifPresent(event -> {
                    Payload.ApprovalRequested request = event.payload(Payload.ApprovalRequested.class);
                    pending.add(new PendingApproval(nodeId, event.outputHash(), request.summary(), request.reasons(),
                            request.files(), request.diffStat(), request.diff()));
                });
            }
        });
        return pending;
    }

    /**
     * Approves the node's pending artifact and promotes it.
     *
     * @param expectedHash hash the approver reviewed (full or a prefix of at least 12 characters), or {@code null}
     */
    public void approve(String nodeId, String by, String comment, String expectedHash)
            throws StaleApprovalException, IOException {
        String approver = HumanDecisions.requireActor(by);
        Event requestEvent = currentRequest(nodeId);
        String hash = requestEvent.outputHash();
        if (expectedHash != null && (expectedHash.length() < 12 || !hash.startsWith(expectedHash))) {
            throw new StaleApprovalException("approval is for artifact " + expectedHash + " but node '" + nodeId
                    + "' now awaits approval of " + hash);
        }
        Artifact artifact = artifacts.get(hash)
                .orElseThrow(() -> new StaleApprovalException("artifact " + hash + " is missing"));
        Path pending = paths.pending(nodeId);
        verifyRetainedProposal(artifact, pending);
        log.append(nodeId, new Payload.Approved(approver, comment == null ? "" : comment, hash), approver, null, hash);

        Payload.ApprovalRequested request = requestEvent.payload(Payload.ApprovalRequested.class);
        NodeCompletion.Candidate candidate = new NodeCompletion.Candidate(log.state().graph().require(nodeId), artifact,
                request.baseHash(), artifact.files().isEmpty() ? null : pending, request.baseHashes(), request.reasons(),
                request.diffStat(), request.diff());
        try {
            completion.complete(candidate, approver, approver);
            FileTrees.deleteTree(pending);
        } catch (PromotionConflictException | WorkflowValidationException exception) {
            throw HumanDecisions.promotionFailed(safeStop, nodeId, approver, "approved artifact", exception);
        }
    }

    /** Rejects the pending artifact; the node re-runs on resume with the comment as feedback. */
    public void reject(String nodeId, String by, String comment) throws StaleApprovalException, IOException {
        String reviewer = HumanDecisions.requireActor(by);
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("--comment is required when rejecting: it becomes the agent's feedback");
        }
        Event request = currentRequest(nodeId);
        log.append(nodeId, new Payload.Rejected(reviewer, comment, request.outputHash()), reviewer, null, request.outputHash());
        FileTrees.deleteTree(paths.pending(nodeId));
    }

    private Event currentRequest(String nodeId) throws StaleApprovalException {
        RunState state = log.state();
        if (state.graph() == null || state.graph().node(nodeId).isEmpty()) {
            throw new StaleApprovalException("unknown node '" + nodeId + "'");
        }
        RunState.NodeState node = state.node(nodeId);
        if (node.status() != NodeStatus.AWAITING_APPROVAL) {
            throw new StaleApprovalException("node '" + nodeId + "' is not awaiting approval (status " + node.status() + ")");
        }
        Event request = HumanDecisions.latest(log.events(), nodeId, EventType.APPROVAL_REQUESTED)
                .orElseThrow(() -> new StaleApprovalException("no approval request for '" + nodeId + "'"));
        if (!Hashing.sameHash(request.outputHash(), node.pendingHash())) {
            throw new StaleApprovalException("approval request for '" + nodeId + "' is superseded");
        }
        return request;
    }

    private static void verifyRetainedProposal(Artifact artifact, Path pending) throws StaleApprovalException {
        Map<String, String> files;
        try {
            files = artifact.files().isEmpty() ? Map.of() : Workspace.readFiles(pending, artifact.files().keySet());
        } catch (IOException exception) {
            throw new StaleApprovalException("retained proposal is incomplete: " + exception.getMessage());
        }
        String recomputed = Hashing.artifactHash(files, artifact.data());
        if (!Hashing.sameHash(recomputed, artifact.hash())) {
            throw new StaleApprovalException("retained proposal changed after approval was requested (expected "
                    + artifact.hash() + ", found " + recomputed + ")");
        }
    }
}
