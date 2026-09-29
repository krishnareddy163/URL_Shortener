package com.example.agentic.cli;

import com.example.agentic.core.report.EventDescriber;
import com.example.agentic.core.state.Event;

import java.io.PrintWriter;
import java.util.function.Consumer;

/** Prints each appended event as {@code [seq] node EVENT detail}, colored only on a capable terminal. */
final class ConsoleTrace implements Consumer<Event> {
    private static final String RESET = "\u001B[0m";

    private final PrintWriter out;
    private final boolean color;

    ConsoleTrace(PrintWriter out, boolean noColorRequested) {
        this.out = out;
        String term = System.getenv("TERM");
        this.color = !noColorRequested && System.console() != null && System.getenv("NO_COLOR") == null
                && term != null && !term.equals("dumb");
    }

    @Override
    public void accept(Event event) {
        String line = String.format("[%3d] %-18s %-24s %s", event.seq(), event.nodeId() == null ? "-" : event.nodeId(),
                event.type(), EventDescriber.describe(event));
        String firstLine = line.lines().findFirst().orElse(line);
        String trimmed = firstLine.length() > 220 ? firstLine.substring(0, 217) + "..." : firstLine;
        out.println(color ? colorFor(event) + trimmed + RESET : trimmed);
        out.flush();
    }

    private static String colorFor(Event event) {
        return switch (event.type()) {
            case NODE_DONE, GATE_PASSED, APPROVED, RUN_COMPLETED -> "\u001B[32m";
            case GATE_FAILED, ATTEMPT_DISCARDED, NODE_FAILED, SAFE_STOP, REJECTED -> "\u001B[31m";
            case APPROVAL_REQUESTED, CLARIFICATION_REQUESTED, RUN_PAUSED, ANSWERED -> "\u001B[33m";
            case REPLAN, INVALIDATED, FALLBACK -> "\u001B[36m";
            default -> "\u001B[90m";
        };
    }
}
