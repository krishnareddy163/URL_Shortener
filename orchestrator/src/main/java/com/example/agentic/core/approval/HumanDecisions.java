package com.example.agentic.core.approval;

import com.example.agentic.core.engine.SafeStop;
import com.example.agentic.core.gate.FailureSignature;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Payload;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Helpers shared by the approval and clarification services. */
final class HumanDecisions {
    private static final String PROMOTION = "promotion";

    private HumanDecisions() {
    }

    /** Human identity is asserted with {@code --by}; it is mandatory for every decision. */
    static String requireActor(String by) {
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("--by is required: every human decision must name its approver");
        }
        return by.strip();
    }

    /**
     * A human decision whose promotion failed (the workspace changed underneath it, or a patch became invalid):
     * fails the node and safe-stops the run atomically, so {@code resume} re-runs the node against the current
     * workspace instead of leaving it stuck waiting.
     *
     * @return the exception to throw to the caller
     */
    static IOException promotionFailed(SafeStop safeStop, String nodeId, String actor, String what, Exception cause) {
        String reason = what + " could not be promoted: " + cause.getMessage();
        safeStop.failNode(nodeId, new Payload.NodeFailed(actor, PROMOTION, FailureSignature.of(PROMOTION, cause.getMessage()),
                cause.getMessage(), false), reason);
        return new IOException(reason + "; run safe-stopped (resume re-runs '" + nodeId + "')", cause);
    }

    /** The most recent event of {@code type} for {@code nodeId}. */
    static Optional<Event> latest(List<Event> events, String nodeId, EventType type) {
        Event found = null;
        for (Event event : events) {
            if (event.type() == type && nodeId.equals(event.nodeId())) {
                found = event;
            }
        }
        return Optional.ofNullable(found);
    }
}
