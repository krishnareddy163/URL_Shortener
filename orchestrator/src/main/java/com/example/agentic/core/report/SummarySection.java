package com.example.agentic.core.report;

import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Run summary: workflow, requirement, status and why the run ended where it did. */
final class SummarySection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        RunState state = input.state();
        out.append("# Run report: `").append(input.runId()).append("`\n\n## Run summary\n\n");
        if (state.graph() == null) {
            out.append("Run has not started.\n\n");
            return;
        }
        out.append("| | |\n|---|---|\n")
                .append("| Workflow | ").append(state.graph().name()).append(" |\n")
                .append("| Requirement | ").append(cell(state.graph().requirement())).append(" |\n")
                .append("| Status | **").append(state.status()).append("** |\n")
                .append("| Exit reason | ").append(cell(exitReason(input))).append(" |\n")
                .append("| Events | ").append(input.events().size()).append(" |\n")
                .append("| Agent calls / attempts | ").append(state.agentCalls()).append(" / ")
                .append(state.totalAttempts()).append(" |\n\n");
    }

    private static String exitReason(ReportInput input) {
        RunState state = input.state();
        return switch (state.status()) {
            case COMPLETED -> "all " + state.nodes().size() + " nodes DONE";
            case SAFE_STOPPED -> "safe-stop: " + state.stopReason();
            case PAUSED -> "paused awaiting human: " + input.events(Payload.RunPaused.class).stream()
                    .reduce((first, last) -> last).map(paused -> String.valueOf(paused.payload().waiting())).orElse("");
            case RUNNING -> "in progress (or interrupted); use resume";
            case NEW -> "not started";
        };
    }
}
