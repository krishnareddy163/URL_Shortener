package com.example.agentic.core.report;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.Payload;

import java.util.Optional;

import static com.example.agentic.core.report.MarkdownText.cell;

/** Human approval decisions, with whether a later invalidation revoked them. */
final class ApprovalsSection implements ReportSection {

    /** The fields shared by an approval and a rejection. */
    private record Decision(String by, String hash, String comment) {
    }

    @Override
    public void render(ReportInput input, StringBuilder out) {
        out.append("## Approvals\n\n| Seq | Node | Decision | Who | When (UTC) | Hash approved | Comment | Revoked later |\n")
                .append("|---:|---|---|---|---|---|---|---|\n");
        for (Event event : input.events()) {
            Optional<Decision> decision = switch (event.type()) {
                case APPROVED -> Optional.of(event.payload(Payload.Approved.class))
                        .map(approved -> new Decision(approved.by(), approved.hash(), approved.comment()));
                case REJECTED -> Optional.of(event.payload(Payload.Rejected.class))
                        .map(rejected -> new Decision(rejected.by(), rejected.hash(), rejected.comment()));
                default -> Optional.empty();
            };
            decision.ifPresent(value -> row(out, input, event, value));
        }
        out.append('\n');
    }

    private static void row(StringBuilder out, ReportInput input, Event event, Decision decision) {
        Optional<Event> revoked = input.events().stream().filter(later -> later.seq() > event.seq()
                && later.type() == EventType.INVALIDATED && event.nodeId().equals(later.nodeId())).findFirst();
        out.append("| ").append(event.seq()).append(" | ").append(event.nodeId()).append(" | ").append(event.type())
                .append(" | ").append(decision.by()).append(" | ").append(MarkdownText.TIME.format(event.ts())).append(" | `")
                .append(Hashing.shortHash(decision.hash())).append("` | ").append(cell(decision.comment()))
                .append(" | ").append(revoked.map(e -> "yes (INVALIDATED seq " + e.seq() + ")").orElse("no")).append(" |\n");
    }
}
