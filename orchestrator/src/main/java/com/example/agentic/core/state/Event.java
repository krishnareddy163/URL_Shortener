package com.example.agentic.core.state;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One immutable fact in a run's append-only log.
 *
 * @param seq        store-assigned sequence number (0 for a draft not yet appended)
 * @param runId      run identifier
 * @param nodeId     node the event concerns, or {@code null} for run-level events
 * @param type       event type
 * @param actor      {@code engine}, an agent id, or a human identity asserted via {@code --by}
 * @param inputHash  node input hash where relevant
 * @param outputHash artifact hash where relevant
 * @param ts         event time from the injected clock
 * @param details    JSON-serializable payload; null values are dropped
 */
public record Event(
        long seq,
        String runId,
        String nodeId,
        EventType type,
        String actor,
        String inputHash,
        String outputHash,
        Instant ts,
        Map<String, Object> details) {

    public Event {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ts, "ts");
        Map<String, Object> copy = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    copy.put(key, value);
                }
            });
        }
        details = Collections.unmodifiableMap(copy);
    }

    public Event withSeq(long assigned) {
        return new Event(assigned, runId, nodeId, type, actor, inputHash, outputHash, ts, details);
    }

    /** The typed payload of this event. */
    public Payload payload() {
        return Payload.decode(this);
    }

    /**
     * The typed payload, checked against the expected record type.
     *
     * @throws IllegalArgumentException if this event carries a different payload type
     */
    public <T extends Payload> T payload(Class<T> expected) {
        Payload payload = payload();
        if (!expected.isInstance(payload)) {
            throw new IllegalArgumentException(type + " carries " + payload.getClass().getSimpleName()
                    + ", not " + expected.getSimpleName());
        }
        return expected.cast(payload);
    }
}
