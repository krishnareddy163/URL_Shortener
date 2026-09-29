package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.approval.ApprovalService;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.core.state.RunState;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only one writer per run; readers are never blocked; the lock is released on close. */
class RunLockTest {

    @TempDir
    Path dir;

    private final ScriptedAgent worker = new ScriptedAgent("worker",
            context -> new Proposal(Map.of("out.txt", "v1"), "done", List.of("requirement"), Map.of()));

    @Test
    void aSecondWriterIsRefusedWhileReadersStillWork() throws Exception {
        TestEngine engine = new TestEngine().agent(worker);
        try (Orchestrator first = engine.openSqlite(dir)) {
            first.start(TestEngine.graph(node("n", "worker", Set.of(), Autonomy.APPROVE_AFTER, 0, null)), dir,
                    RunMetadata.none());

            try (Orchestrator reader = engine.openSqlite(dir)) {
                assertThat(reader.state().status()).as("reading needs no lock").isEqualTo(RunState.RunStatus.PAUSED);
                assertThat(reader.pendingApprovals()).as("pending is a read").extracting(ApprovalService.PendingApproval::nodeId)
                        .containsExactly("n");
                assertThatThrownBy(reader::approvals).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("is being changed by another process").hasMessageContaining("pid ");
            }
            try (Orchestrator second = engine.openSqlite(dir)) {
                assertThatThrownBy(second::resume).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("is being changed by another process");
            }
        }
        try (Orchestrator afterClose = engine.openSqlite(dir)) {
            afterClose.approvals().approve("n", "alice", "ok", null);
            assertThat(afterClose.resume()).isEqualTo(RunOutcome.COMPLETED);
        }
    }

    @Test
    void aHeldLockRefusesOthersAndIsFreeAgainAfterClose() {
        RunLock held = RunLock.acquire(dir);
        assertThatThrownBy(() -> RunLock.acquire(dir)).isInstanceOf(IllegalStateException.class);
        held.close();
        RunLock.acquire(dir).close();
    }
}
