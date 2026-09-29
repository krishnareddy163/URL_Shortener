package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.Payload;

import static com.example.agentic.core.report.MarkdownText.cell;

/** INVALIDATED and REPLAN events in order. */
final class ReplanHistorySection implements ReportSection {

    @Override
    public void render(ReportInput input, StringBuilder out) {
        out.append("## Invalidation and replan history\n\n| Seq | Event | Node | Detail |\n|---:|---|---|---|\n");
        boolean any = false;
        for (Event event : input.events()) {
            if (event.type() == EventType.INVALIDATED || event.type() == EventType.REPLAN) {
                any = true;
                out.append("| ").append(event.seq()).append(" | ").append(event.type()).append(" | ")
                        .append(event.nodeId() == null ? "" : event.nodeId()).append(" | ").append(cell(detail(event)))
                        .append(" |\n");
            }
        }
        if (!any) {
            out.append("| | none | | |\n");
        }
    }

    private static String detail(Event event) {
        if (event.payload() instanceof Payload.Replan replan && replan.isInvalidation()) {
            return "cascade " + replan.cascade() + " (" + Hashing.shortHash(replan.oldHash()) + " → "
                    + Hashing.shortHash(replan.newHash()) + ")";
        }
        return EventDescriber.describe(event);
    }
}
