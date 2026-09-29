package com.example.agentic.core.state;

import java.util.List;
import java.util.Objects;

/**
 * An event paired with its decoded payload, for readers that care about one event type.
 *
 * @param event   the stored event (sequence, node, actor, time, hashes)
 * @param payload its typed body
 * @param <T>     payload record type
 */
public record TypedEvent<T extends Payload>(Event event, T payload) {

    public TypedEvent {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(payload, "payload");
    }

    /** The events whose payload is {@code type}, in log order; other events are not decoded. */
    public static <T extends Payload> List<TypedEvent<T>> of(List<Event> events, Class<T> type) {
        return events.stream()
                .filter(event -> Payload.classFor(event.type()) == type)
                .map(event -> new TypedEvent<>(event, event.payload(type)))
                .toList();
    }
}
