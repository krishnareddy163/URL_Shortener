package com.example.agentic.core.report;

import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunState;

import java.util.List;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Questions raised, their answers, and the assumptions recorded in DONE artifacts. */
final class ClarificationsSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        RunState state = input.state();
        out.append("## Clarifications and assumptions\n\n");
        if (state.questions().isEmpty()) {
            out.append("No blocking questions were raised.\n\n");
        } else {
            out.append("| Question | Blocking | Answer |\n|---|---|---|\n");
            state.questions().values().forEach(question -> out.append("| `").append(question.id()).append("` ")
                    .append(cell(question.text())).append(" | ").append(question.blocking()).append(" | ")
                    .append(cell(state.answers().getOrDefault(question.id(), "(unanswered)"))).append(" |\n"));
            out.append('\n');
        }
        state.nodes().forEach((id, node) -> {
            if (node.status() != NodeStatus.DONE || node.currentHash() == null) {
                return;
            }
            input.artifacts().get(node.currentHash()).ifPresent(artifact -> {
                if (artifact.data().get("assumptions") instanceof List<?> assumptions && !assumptions.isEmpty()) {
                    out.append("Recorded assumptions (`").append(id).append("`):\n\n");
                    assumptions.forEach(assumption -> out.append("- ").append(assumption).append('\n'));
                    out.append('\n');
                }
            });
        });
    }
}
