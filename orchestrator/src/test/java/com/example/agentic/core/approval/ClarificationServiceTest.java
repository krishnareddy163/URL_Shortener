package com.example.agentic.core.approval;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.core.state.RunState;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClarificationServiceTest {

    @TempDir
    Path dir;

    private final ScriptedAgent worker = new ScriptedAgent("worker", context -> context.nodeId().equals("seed")
            ? new Proposal(Map.of("notes.md", "seed\n"), "seed", List.of("requirement"), Map.of())
            : new Proposal(Map.of("notes.md", "seed\nasked\n"), "asks", List.of("requirement"), Map.of("ambiguities",
                    List.of(Map.of("id", "q", "question", "which?", "blocking", true, "options", List.of("a", "b"))))));

    private Orchestrator pausedForClarification() throws Exception {
        Orchestrator orchestrator = new TestEngine().agent(worker).open(dir, new InMemoryEventStore());
        RunOutcome outcome = orchestrator.start(TestEngine.graph(
                node("seed", "worker", Set.of(), Autonomy.AUTO, 0, null),
                node("ask", "worker", Set.of("seed"), Autonomy.AUTO, 0, null)), dir, RunMetadata.none());
        assertThat(outcome).isEqualTo(RunOutcome.PAUSED);
        assertThat(orchestrator.state().status("ask")).isEqualTo(NodeStatus.AWAITING_CLARIFICATION);
        return orchestrator;
    }

    @Test
    void answeringTheBlockingQuestionCompletesTheWaitingNode() throws Exception {
        try (Orchestrator orchestrator = pausedForClarification()) {
            orchestrator.clarifications().answer("q", "a", "carol");

            assertThat(orchestrator.state().status("ask")).isEqualTo(NodeStatus.DONE);
            assertThat(Files.readString(orchestrator.workspace().root().resolve("notes.md"))).isEqualTo("seed\nasked\n");
        }
    }

    @Test
    void promotionConflictAfterAnAnswerSafeStopsSoResumeCanRerunTheNode() throws Exception {
        try (Orchestrator orchestrator = pausedForClarification()) {
            Files.writeString(orchestrator.workspace().root().resolve("notes.md"), "changed while waiting\n");

            assertThatThrownBy(() -> orchestrator.clarifications().answer("q", "a", "carol"))
                    .isInstanceOf(IOException.class).hasMessageContaining("safe-stopped");

            assertThat(orchestrator.state().status()).isEqualTo(RunState.RunStatus.SAFE_STOPPED);
            assertThat(orchestrator.state().status("ask")).isEqualTo(NodeStatus.FAILED);
            assertThat(Files.readString(orchestrator.workspace().root().resolve("notes.md"))).isEqualTo("changed while waiting\n");
        }
    }
}
