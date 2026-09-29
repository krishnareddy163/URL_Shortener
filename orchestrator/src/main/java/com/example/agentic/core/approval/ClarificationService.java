package com.example.agentic.core.approval;

import com.example.agentic.core.engine.ArtifactRecorder;
import com.example.agentic.core.engine.Clarifications;
import com.example.agentic.core.engine.NodeCompletion;
import com.example.agentic.core.engine.RunPaths;
import com.example.agentic.core.engine.SafeStop;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.workspace.FileTrees;
import com.example.agentic.core.workspace.PromotionConflictException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Human answers to agent questions. Answers are folded into the asking node's artifact, so:
 * answering the last blocking question finalizes a waiting node, and changing an answer after the node
 * completed produces a new artifact hash, which invalidates everything downstream.
 */
public final class ClarificationService {
    private final RunLog log;
    private final ArtifactRecorder artifacts;
    private final RunPaths paths;
    private final NodeCompletion completion;
    private final SafeStop safeStop;

    public ClarificationService(RunLog log, ArtifactRecorder artifacts, RunPaths paths, NodeCompletion completion,
                                SafeStop safeStop) {
        this.log = log;
        this.artifacts = artifacts;
        this.paths = paths;
        this.completion = completion;
        this.safeStop = safeStop;
    }

    /** Open questions (asked and not yet answered). */
    public List<RunState.Question> openQuestions() {
        RunState state = log.state();
        return state.questions().values().stream().filter(question -> !state.answers().containsKey(question.id())).toList();
    }

    /**
     * Records an answer and applies its consequences.
     *
     * @return a one-line description of what happened
     */
    public String answer(String questionId, String answer, String by) throws IOException {
        String actor = HumanDecisions.requireActor(by);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        RunState state = log.state();
        RunState.Question question = state.questions().get(questionId);
        if (question == null) {
            throw new IllegalArgumentException("unknown question '" + questionId + "'; known: " + state.questions().keySet());
        }
        log.append(question.nodeId(), new Payload.Answered(questionId, answer.strip(), state.answers().get(questionId)),
                actor, null, null);

        RunState updated = log.state();
        RunState.NodeState node = updated.node(question.nodeId());
        try {
            if (node.status() == NodeStatus.AWAITING_CLARIFICATION) {
                return finalizeWaitingNode(question.nodeId(), node, updated, actor);
            }
            if (node.status() == NodeStatus.DONE && node.baseHash() != null) {
                return refoldCompletedNode(question.nodeId(), node, updated, actor);
            }
        } catch (PromotionConflictException | WorkflowValidationException exception) {
            throw HumanDecisions.promotionFailed(safeStop, question.nodeId(), actor, "answered artifact", exception);
        }
        return "answer recorded; node '" + question.nodeId() + "' will use it when it runs";
    }

    private String finalizeWaitingNode(String nodeId, RunState.NodeState node, RunState state, String actor)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        Artifact base = baseArtifact(node.pendingHash());
        List<Map<?, ?>> stillOpen = Clarifications.unansweredBlocking(base.data(), state.answers());
        if (!stillOpen.isEmpty()) {
            return "answer recorded; " + stillOpen.size() + " blocking question(s) still open for '" + nodeId + "'";
        }
        Payload.ClarificationRequested request = HumanDecisions.latest(log.events(), nodeId, EventType.CLARIFICATION_REQUESTED)
                .orElseThrow(() -> new IllegalStateException("no clarification request for " + nodeId))
                .payload(Payload.ClarificationRequested.class);
        Artifact finalArtifact = fold(base, state.answers());
        Node definition = state.graph().require(nodeId);
        boolean done = completion.settle(new NodeCompletion.Candidate(definition, finalArtifact, base.hash(),
                base.files().isEmpty() ? null : paths.pending(nodeId), request.baseHashes(), request.riskReasons(),
                request.diffStat(), ""), actor);
        if (done) {
            FileTrees.deleteTree(paths.pending(nodeId));
            return "all blocking questions answered; '" + nodeId + "' completed with artifact " + finalArtifact.hash();
        }
        return "all blocking questions answered; '" + nodeId + "' now awaits approval";
    }

    private String refoldCompletedNode(String nodeId, RunState.NodeState node, RunState state, String actor)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        Artifact base = baseArtifact(node.baseHash());
        Artifact finalArtifact = fold(base, state.answers());
        if (Hashing.sameHash(finalArtifact.hash(), node.currentHash())) {
            return "answer unchanged in effect; '" + nodeId + "' keeps artifact " + node.currentHash();
        }
        Node definition = state.graph().require(nodeId);
        completion.settle(new NodeCompletion.Candidate(definition, finalArtifact, base.hash(), null, Map.of(), List.of(),
                "", ""), actor);
        return "'" + nodeId + "' has a new artifact " + finalArtifact.hash() + "; downstream nodes were invalidated";
    }

    private Artifact fold(Artifact base, Map<String, String> answers) {
        Artifact folded = Artifact.of(base.nodeId(), base.derivedFrom(), base.rationale(), base.files(),
                Clarifications.fold(base.data(), answers));
        artifacts.save(folded);
        return folded;
    }

    private Artifact baseArtifact(String hash) {
        return artifacts.require(hash);
    }
}
