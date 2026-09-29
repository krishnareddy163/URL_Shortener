package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.RunState;

import java.util.Optional;

/** Mermaid graph of the final plan, preceded by the plan just before the first re-plan when there was one. */
final class GraphSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        out.append("## Workflow graph\n\n");
        if (input.state().graph() == null) {
            return;
        }
        Optional<Event> firstReplan = input.events().stream().filter(event -> event.type() == EventType.REPLAN).findFirst();
        if (firstReplan.isPresent()) {
            long seq = firstReplan.get().seq();
            RunState before = RunState.fold(input.events().stream().filter(event -> event.seq() < seq).toList());
            out.append("### Before re-plan (state just before seq ").append(seq).append(")\n\n")
                    .append(MermaidRenderer.render(before)).append("\n### After (final)\n\n");
        }
        out.append(MermaidRenderer.render(input.state())).append('\n');
    }
}
