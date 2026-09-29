package com.example.agentic.core.approval;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.engine.RunOutcome;
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
import java.util.concurrent.atomic.AtomicReference;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalServiceTest {

    @TempDir
    Path dir;

    private final AtomicReference<String> feedback = new AtomicReference<>();
    private final ScriptedAgent worker = new ScriptedAgent("worker", context -> {
        if (context.feedback() != null) {
            feedback.set(context.feedback());
        }
        return new Proposal(Map.of("design.md", "design v" + context.attempt() + "\n"), "design attempt " + context.attempt(),
                List.of("requirement"), Map.of());
    });

    private Orchestrator pausedForApproval() throws Exception {
        Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore());
        RunOutcome outcome = orchestrator.start(TestEngine.graph(
                node("design", "worker", Set.of(), Autonomy.APPROVE_AFTER, 0, null, "artifact-metadata")), dir, RunMetadata.none());
        assertThat(outcome).isEqualTo(RunOutcome.PAUSED);
        return orchestrator;
    }

    @Test
    void approvalPromotesTheExactArtifactAndRecordsWhoWhenAndWhich() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            ApprovalService.PendingApproval pending = orchestrator.approvals().pending().getFirst();
            assertThat(pending.reasons()).anyMatch(reason -> reason.contains("APPROVE_AFTER"));
            assertThat(pending.files()).containsExactly("design.md");
            assertThat(pending.diff()).contains("+design v1");
            assertThat(Files.exists(orchestrator.workspace().root().resolve("design.md"))).isFalse();

            orchestrator.approvals().approve("design", "alice", "looks right", pending.hash().substring(0, 12));

            Event approved = orchestrator.log().events().stream().filter(e -> e.type() == EventType.APPROVED).findFirst().orElseThrow();
            assertThat(approved.actor()).isEqualTo("alice");
            assertThat(approved.details()).containsEntry("by", "alice").containsEntry("comment", "looks right")
                    .containsEntry("hash", pending.hash());
            assertThat(orchestrator.state().status("design")).isEqualTo(NodeStatus.DONE);
            assertThat(Files.readString(orchestrator.workspace().root().resolve("design.md"))).isEqualTo("design v1\n");
            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.COMPLETED);
        }
    }

    @Test
    void approvalOfADifferentHashIsStale() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            ApprovalService approvals = orchestrator.approvals();
            assertThatThrownBy(() -> approvals.approve("design", "alice", "ok", "0000000000000000"))
                    .isInstanceOf(StaleApprovalException.class).hasMessageContaining("now awaits approval of");
            assertThat(orchestrator.state().status("design")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
        }
    }

    @Test
    void approvalIsStaleIfTheRetainedProposalWasTamperedWith() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            Files.writeString(orchestrator.runDir().resolve("pending/design/design.md"), "sneaky edit\n");

            ApprovalService approvals = orchestrator.approvals();
            assertThatThrownBy(() -> approvals.approve("design", "alice", "ok", null))
                    .isInstanceOf(StaleApprovalException.class).hasMessageContaining("changed after approval was requested");
            assertThat(orchestrator.log().events()).noneMatch(event -> event.type() == EventType.APPROVED);
        }
    }

    @Test
    void approverIdentityIsMandatory() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            ApprovalService approvals = orchestrator.approvals();
            assertThatThrownBy(() -> approvals.approve("design", " ", "ok", null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--by is required");
            assertThatThrownBy(() -> approvals.reject("design", null, "no"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void approvingANodeThatIsNotWaitingIsStale() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            orchestrator.approvals().approve("design", "alice", "ok", null);
            ApprovalService approvals = orchestrator.approvals();
            assertThatThrownBy(() -> approvals.approve("design", "bob", "again", null))
                    .isInstanceOf(StaleApprovalException.class).hasMessageContaining("not awaiting approval");
        }
    }

    @Test
    void rejectionReRunsTheNodeWithTheCommentAsFeedback() throws Exception {
        try (Orchestrator orchestrator = pausedForApproval()) {
            String firstHash = orchestrator.approvals().pending().getFirst().hash();

            orchestrator.approvals().reject("design", "bob", "add the error model");
            assertThat(orchestrator.state().status("design")).isEqualTo(NodeStatus.PENDING);
            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.PAUSED);

            ApprovalService.PendingApproval second = orchestrator.approvals().pending().getFirst();
            assertThat(feedback.get()).isEqualTo("add the error model");
            assertThat(second.hash()).isNotEqualTo(firstHash);
            assertThat(second.diff()).contains("+design v2");
            assertThat(orchestrator.state().totalAttempts()).as("rejection counts toward attempts").isEqualTo(3);
        }
    }

    @Test
    void escalateOnRiskOnlyAsksWhenARiskRuleFires() throws Exception {
        ScriptedAgent developer = new ScriptedAgent("worker", context -> new Proposal(
                context.nodeId().equals("risky") ? Map.of("pom.xml", "<project/>") : Map.of("Safe.java", "class Safe {}"),
                "change", List.of("requirement"), Map.of()));
        try (Orchestrator orchestrator = new TestEngine().agent(developer).open(dir, new InMemoryEventStore())) {
            orchestrator.start(TestEngine.graph(
                    node("risky", "worker", Set.of(), Autonomy.ESCALATE_ON_RISK, 0, null),
                    node("safe", "worker", Set.of(), Autonomy.ESCALATE_ON_RISK, 0, null)), dir, RunMetadata.none());

            assertThat(orchestrator.state().status("risky")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
            assertThat(orchestrator.state().status("safe")).isEqualTo(NodeStatus.DONE);
            assertThat(orchestrator.approvals().pending().getFirst().reasons()).anyMatch(reason -> reason.startsWith("PomChangeRule"));
        }
    }
}
