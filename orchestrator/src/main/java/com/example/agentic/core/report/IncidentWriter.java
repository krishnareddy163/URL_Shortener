package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.TypedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes {@code incident.md} on safe-stop: failing node and reason, the signature history of discarded
 * attempts, the last gate output, preserved DONE nodes, and how to resume or restart.
 */
public final class IncidentWriter {
    private static final int CELL_LIMIT = 200;

    private IncidentWriter() {
    }

    public static void write(Path file, String runId, List<Event> events, String nodeId, String reason) throws IOException {
        RunState state = RunState.fold(events);
        StringBuilder out = new StringBuilder("# Incident: run `").append(runId).append("` safe-stopped\n\n")
                .append("- **Failing node:** ").append(nodeId == null ? "(run-level)" : "`" + nodeId + "`").append('\n')
                .append("- **Reason:** ").append(reason.replace("\n", " ")).append('\n')
                .append("- **Agent calls / attempts used:** ").append(state.agentCalls()).append(" / ")
                .append(state.totalAttempts()).append("\n\n");
        List<Event> relevant = events.stream().filter(event -> nodeId == null || nodeId.equals(event.nodeId())).toList();
        signatureHistory(out, relevant);
        lastGateOutput(out, relevant, reason);
        nodeStatus(out, state);
        out.append("""

                ## How to recover

                - DONE nodes and their promoted files are preserved; they will not be re-executed.
                - Fix the cause (fixture, policy, or environment), then run `orchestrator resume RUN_ID`.
                  Failed and skipped nodes return to PENDING and run again.
                - Budgets are cumulative for a run. If a budget stopped the run, start a new run instead.
                - To discard this run entirely, delete `runs/RUN_ID/` and start again with `orchestrator run`.
                """.replace("RUN_ID", runId));
        Files.createDirectories(Objects.requireNonNull(file.toAbsolutePath().getParent()));
        Files.writeString(file, out.toString());
    }

    private static void signatureHistory(StringBuilder out, List<Event> events) {
        out.append("## Signature history\n\n| Seq | Node | Agent | Attempt | Gate | Signature | Reason |\n|---|---|---|---|---|---|---|\n");
        for (Event event : events) {
            switch (event.type()) {
                case ATTEMPT_DISCARDED -> {
                    Payload.AttemptDiscarded discarded = event.payload(Payload.AttemptDiscarded.class);
                    out.append("| ").append(event.seq()).append(" | ").append(event.nodeId()).append(" | ")
                            .append(discarded.agent()).append(" | ").append(discarded.attempt()).append(" | ")
                            .append(discarded.gate()).append(" | `").append(Hashing.shortHash(discarded.signature()))
                            .append("` | ").append(MarkdownText.cell(discarded.reason(), CELL_LIMIT)).append(" |\n");
                }
                case FALLBACK -> {
                    Payload.Fallback fallback = event.payload(Payload.Fallback.class);
                    out.append("| ").append(event.seq()).append(" | ").append(event.nodeId()).append(" | FALLBACK ")
                            .append(fallback.from()).append(" → ").append(fallback.to()).append(" | | | | ")
                            .append(MarkdownText.cell(fallback.cause(), CELL_LIMIT)).append(" |\n");
                }
                default -> {
                    // Only discarded attempts and fallbacks form the signature history.
                }
            }
        }
    }

    private static void lastGateOutput(StringBuilder out, List<Event> events, String reason) {
        out.append("\n## Last gate output\n\n");
        Optional<TypedEvent<Payload.GateFailed>> lastFailure = TypedEvent.of(events, Payload.GateFailed.class).stream()
                .reduce((first, last) -> last);
        if (lastFailure.isEmpty()) {
            out.append("No gate failed; the stop was caused by: ").append(reason).append('\n');
            return;
        }
        Payload.GateFailed failure = lastFailure.get().payload();
        out.append("Gate `").append(failure.gate()).append("` on `").append(lastFailure.get().event().nodeId())
                .append("`:\n\n```\n").append(failure.reason()).append("\n```\n");
    }

    private static void nodeStatus(StringBuilder out, RunState state) {
        out.append("\n## Node status\n\n| Node | Status | Artifact |\n|---|---|---|\n");
        for (Map.Entry<String, RunState.NodeState> entry : state.nodes().entrySet()) {
            boolean done = entry.getValue().status() == NodeStatus.DONE;
            out.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().status()).append(" | ")
                    .append(done ? "`" + Hashing.shortHash(entry.getValue().currentHash()) + "` (preserved)" : "")
                    .append(" |\n");
        }
    }
}
