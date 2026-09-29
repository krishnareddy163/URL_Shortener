package com.example.agentic.core.engine;

import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.GraphPatch;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ImmutableMaps;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.workspace.PromotionConflictException;
import com.example.agentic.core.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single path by which a node's output becomes accepted work, shared by the node runner, approvals and
 * clarifications: decide whether a human must approve, otherwise promote the staged files, append
 * {@code NODE_DONE}, apply any graph patch and invalidate downstream nodes if the artifact hash changed.
 */
public final class NodeCompletion {
    private static final int MAX_DIFF_IN_EVENT = 200_000;

    private final RunLog log;
    private final Workspace workspace;
    private final RunPaths paths;
    private final ReplanService replan;

    public NodeCompletion(RunLog log, Workspace workspace, RunPaths paths, ReplanService replan) {
        this.log = log;
        this.workspace = workspace;
        this.paths = paths;
        this.replan = replan;
    }

    /**
     * A gate-validated artifact waiting to be settled.
     *
     * @param node        node that produced it
     * @param artifact    final artifact (answers already folded in)
     * @param baseHash    hash of the agent's raw output
     * @param stagedTree  staged tree holding the files, or {@code null} if nothing needs promotion
     * @param baseHashes  hash of each proposed file at staging time
     * @param riskReasons risk-rule flags
     * @param diffStat    per-file change summary
     * @param diffText    unified diff
     */
    public record Candidate(Node node, Artifact artifact, String baseHash, Path stagedTree, Map<String, String> baseHashes,
                            List<String> riskReasons, String diffStat, String diffText) {

        public Candidate {
            baseHashes = Map.copyOf(ImmutableMaps.withoutNulls(baseHashes));
            riskReasons = riskReasons == null ? List.of() : List.copyOf(riskReasons);
            diffStat = diffStat == null ? "" : diffStat;
            diffText = diffText == null ? "" : diffText;
        }
    }

    /** Whether autonomy and risk flags require a human checkpoint before promotion. */
    public static boolean requiresApproval(Node node, List<String> riskReasons) {
        return node.autonomy() == Autonomy.APPROVE_AFTER
                || (node.autonomy() == Autonomy.ESCALATE_ON_RISK && !riskReasons.isEmpty());
    }

    /**
     * Completes the node, or requests approval when required. The candidate's staged tree must already be the
     * node's pending directory if approval may be needed.
     *
     * @return {@code true} if the node is DONE, {@code false} if it now awaits approval
     */
    public boolean settle(Candidate candidate, String actor)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        if (requiresApproval(candidate.node(), candidate.riskReasons())) {
            requestApproval(candidate);
            return false;
        }
        complete(candidate, actor, null);
        return true;
    }

    /** Appends APPROVAL_REQUESTED with everything a reviewer needs; the staged tree stays under pending/. */
    public void requestApproval(Candidate candidate) {
        Node node = candidate.node();
        List<String> reasons = new ArrayList<>();
        if (node.autonomy() == Autonomy.APPROVE_AFTER) {
            reasons.add("APPROVE_AFTER: human sign-off required");
        }
        reasons.addAll(candidate.riskReasons());
        Payload.ApprovalRequested request = new Payload.ApprovalRequested(candidate.artifact().rationale(),
                node.autonomy().name(), reasons, candidate.artifact().files().keySet().stream().sorted().toList(),
                candidate.baseHash(), candidate.baseHashes(), candidate.diffStat(), truncate(candidate.diffText()));
        log.append(node.id(), request, RunLog.ENGINE, null, candidate.artifact().hash());
    }

    /**
     * Promotes, records NODE_DONE, applies any graph patch, and invalidates downstream on a hash change.
     *
     * @param approvedBy approver when completing after a human decision, otherwise {@code null}
     */
    public void complete(Candidate candidate, String actor, String approvedBy)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        Node node = candidate.node();
        Artifact artifact = candidate.artifact();
        ReplanService.PatchParse parse = ReplanService.patchIn(artifact.data());
        Optional<String> malformed = parse.error();
        if (malformed.isPresent()) {
            throw new WorkflowValidationException(List.of(malformed.get()));
        }
        Optional<GraphPatch> patch = parse.patch();
        if (patch.isPresent()) {
            List<String> errors = replan.validate(patch.get(), log.state());
            if (!errors.isEmpty()) {
                throw new WorkflowValidationException(errors);
            }
        }
        List<String> promoted = candidate.stagedTree() == null || candidate.baseHashes().isEmpty() ? List.of()
                : workspace.promote(candidate.stagedTree(), candidate.baseHashes(), paths.promotion(node.id(), artifact.hash()));
        String commit = promoted.isEmpty() ? null : workspace.history()
                .commit(promoted, commitSubject(node.id(), artifact.rationale()), commitBody(node, artifact, approvedBy), node.agent())
                .orElse(null);
        String previousHash = log.state().node(node.id()).currentHash();
        log.append(node.id(), new Payload.NodeDone(promoted, candidate.baseHash(), approvedBy, commit), actor, null, artifact.hash());
        if (patch.isPresent()) {
            replan.apply(patch.get(), node.id());
        }
        replan.invalidateDownstream(node.id(), previousHash, artifact.hash(), "artifact of '" + node.id() + "' changed");
    }

    /** "node: first sentence of the rationale", at most 72 characters. */
    static String commitSubject(String nodeId, String rationale) {
        String first = rationale == null ? "" : rationale.strip().split("(?<=\\.)\\s|\\R", 2)[0];
        String subject = nodeId + ": " + (first.isEmpty() ? "promote" : first);
        return subject.length() <= 72 ? subject : subject.substring(0, 71) + "\u2026";
    }

    private String commitBody(Node node, Artifact artifact, String approvedBy) {
        StringBuilder body = new StringBuilder(artifact.rationale() == null ? "" : artifact.rationale().strip())
                .append("\n\nNode: ").append(node.id())
                .append("\nAgent: ").append(node.agent())
                .append("\nArtifact: ").append(artifact.hash())
                .append("\nRun: ").append(log.runId());
        if (approvedBy != null) {
            body.append("\nApproved-by: ").append(approvedBy);
        }
        return body.toString();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_DIFF_IN_EVENT ? text : text.substring(0, MAX_DIFF_IN_EVENT) + "\n... (truncated)";
    }
}
