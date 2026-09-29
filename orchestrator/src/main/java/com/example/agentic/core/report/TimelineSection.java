package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Every event, one row each. */
final class TimelineSection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        out.append("## Timeline\n\n| Seq | Time (UTC) | Node | Event | Actor | Detail |\n|---:|---|---|---|---|---|\n");
        for (Event event : input.events()) {
            out.append("| ").append(event.seq()).append(" | ").append(MarkdownText.TIME.format(event.ts())).append(" | ")
                    .append(event.nodeId() == null ? "" : event.nodeId()).append(" | ").append(event.type()).append(" | ")
                    .append(event.actor()).append(" | ").append(cell(EventDescriber.describe(event))).append(" |\n");
        }
        out.append('\n');
    }
}
