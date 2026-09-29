package com.example.agentic.core.engine;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.policy.RiskRule;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.workspace.Diff;
import com.example.agentic.core.workspace.PromotionConflictException;
import com.example.agentic.core.workspace.Staging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settles an accepted attempt: builds the artifact (lineage resolved to upstream hashes), evaluates risk rules,
 * then pauses for clarification, requests approval, or promotes.
 */
final class Settlement {
    private final RunLog log;
    private final ArtifactRecorder artifacts;
    private final List<RiskRule> riskRules;
    private final RunPaths paths;
    private final NodeCompletion completion;

    Settlement(RunLog log, ArtifactRecorder artifacts, List<RiskRule> riskRules, RunPaths paths, NodeCompletion completion) {
        this.log = log;
        this.artifacts = artifacts;
        this.riskRules = List.copyOf(riskRules);
        this.paths = paths;
        this.completion = completion;
    }

    /** Settles the attempt; the staging directory is consumed (promoted, retained under pending/, or deleted). */
    NodeRunner.Result settle(NodeInputs inputs, Proposal proposal, Staging staging, Diff diff)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        Node node = inputs.node();
        List<String> lineage = proposal.derivedFrom().stream()
                .map(source -> inputs.upstream().containsKey(source) ? source + "@" + inputs.upstream().get(source).hash() : source)
                .toList();
        Artifact base = Artifact.of(node.id(), lineage, proposal.rationale(), proposal.files(), proposal.data());
        artifacts.save(base);
        List<String> riskReasons = new ArrayList<>();
        riskRules.forEach(rule -> rule.flag(diff).ifPresent(riskReasons::add));

        Map<String, String> answers = log.state().answers();
        List<Map<?, ?>> blocking = Clarifications.unansweredBlocking(proposal.data(), answers);
        if (!blocking.isEmpty()) {
            staging.retainAt(paths.pending(node.id()));
            for (Map<?, ?> question : blocking) {
                log.append(node.id(), clarificationRequest(question, staging, riskReasons, diff), node.agent(), null, base.hash());
            }
            return NodeRunner.Result.WAITING;
        }
        Artifact finalArtifact = Artifact.of(node.id(), lineage, proposal.rationale(), proposal.files(),
                Clarifications.fold(proposal.data(), answers));
        artifacts.save(finalArtifact);
        if (NodeCompletion.requiresApproval(node, riskReasons)) {
            completion.requestApproval(new NodeCompletion.Candidate(node, finalArtifact, base.hash(),
                    staging.retainAt(paths.pending(node.id())), staging.baseHashes(), riskReasons, diff.stat(), diff.unified()));
            return NodeRunner.Result.WAITING;
        }
        completion.complete(new NodeCompletion.Candidate(node, finalArtifact, base.hash(), staging.path(),
                staging.baseHashes(), riskReasons, diff.stat(), diff.unified()), RunLog.ENGINE, null);
        staging.discard();
        return NodeRunner.Result.DONE;
    }

    private static Payload.ClarificationRequested clarificationRequest(Map<?, ?> question, Staging staging,
                                                                        List<String> riskReasons, Diff diff) {
        List<String> options = question.get("options") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        Object assumption = question.get("assumptionIfUnanswered");
        return new Payload.ClarificationRequested(String.valueOf(question.get("id")), String.valueOf(question.get("question")),
                true, options, assumption == null ? null : String.valueOf(assumption), staging.baseHashes(), riskReasons,
                diff.stat());
    }
}
