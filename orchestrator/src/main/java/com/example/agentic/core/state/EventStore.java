package com.example.agentic.core.state;

import java.util.List;

/**
 * Append-only event persistence. There is deliberately no update or delete operation; implementations
 * must also refuse mutation at the storage layer.
 */
public interface EventStore extends AutoCloseable {

    /** Appends the draft and returns it with its assigned sequence number. */
    Event append(Event draft);

    /** All events of a run in sequence order. */
    List<Event> read(String runId);

    @Override
    void close();
}
