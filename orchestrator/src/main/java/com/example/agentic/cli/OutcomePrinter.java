package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.state.NodeStatus;

import java.io.PrintWriter;

/** Prints how a run invocation ended and what the human should do next. */
final class OutcomePrinter {

    private OutcomePrinter() {
    }

    static void print(PrintWriter out, String runId, RunOutcome outcome, Orchestrator orchestrator) {
        switch (outcome) {
            case COMPLETED -> out.printf("%nRun %s COMPLETED (exit 0).%n", runId);
            case PAUSED -> {
                out.printf("%nRun %s PAUSED awaiting a human (exit 10):%n", runId);
                orchestrator.state().nodes().forEach((id, node) -> {
                    if (node.status() == NodeStatus.AWAITING_APPROVAL || node.status() == NodeStatus.AWAITING_CLARIFICATION) {
                        out.printf("  - %s: %s%n", id, node.status());
                    }
                });
                out.printf("Next: orchestrator pending %s%n", runId);
            }
            case SAFE_STOPPED -> out.printf("%nRun %s SAFE-STOPPED (exit 20): %s%nSee %s%n", runId,
                    orchestrator.state().stopReason(), orchestrator.runDir().resolve("incident.md"));
        }
        out.flush();
    }
}
