package com.example.agentic.core.state;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The engine's single write path for a run: appends events to the {@link EventStore} and applies each one
 * to the live {@link RunState}. Appends are serialized so parallel nodes observe a total order.
 */
public final class RunLog {
    public static final String ENGINE = "engine";

    private final EventStore store;
    private final String runId;
    private final Clock clock;
    private final Consumer<Event> listener;
    private RunState state;

    public RunLog(EventStore store, String runId, Clock clock, Consumer<Event> listener) {
        this.store = Objects.requireNonNull(store);
        this.runId = Objects.requireNonNull(runId);
        this.clock = Objects.requireNonNull(clock);
        this.listener = listener == null ? event -> { } : listener;
        this.state = RunState.fold(store.read(runId));
    }

    /**
     * Appends a typed event.
     *
     * @param nodeId     node the event concerns, or {@code null} for run-level events
     * @param payload    typed body; its record type determines the event type
     * @param actor      {@code engine}, an agent id, or a human identity
     * @param inputHash  node input hash, or {@code null}
     * @param outputHash artifact hash, or {@code null}
     */
    public synchronized Event append(String nodeId, Payload payload, String actor, String inputHash, String outputHash) {
        Event draft = new Event(0, runId, nodeId, payload.type(), actor == null ? ENGINE : actor, inputHash, outputHash,
                clock.instant(), payload.toDetails());
        // Decode before persisting: an event the fold cannot read must never reach the append-only log.
        draft.payload();
        Event stored = store.append(draft);
        state = state.apply(stored);
        listener.accept(stored);
        return stored;
    }

    /** Appends an engine event with no hashes. */
    public Event append(String nodeId, Payload payload) {
        return append(nodeId, payload, ENGINE, null, null);
    }

    public synchronized RunState state() {
        return state;
    }

    /** Events as persisted (a fresh read from the store). */
    public List<Event> events() {
        return store.read(runId);
    }

    public String runId() {
        return runId;
    }

    public Clock clock() {
        return clock;
    }
}
