package com.example.agentic.support;

import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Append-only in-memory store for fast engine tests (also shows EventStore is swappable). */
public final class InMemoryEventStore implements EventStore, ArtifactStore {
    private final List<Event> events = new ArrayList<>();
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();

    @Override
    public synchronized Event append(Event draft) {
        Event stored = draft.withSeq(events.size() + 1L);
        events.add(stored);
        return stored;
    }

    @Override
    public synchronized List<Event> read(String runId) {
        return events.stream().filter(event -> event.runId().equals(runId)).toList();
    }

    @Override
    public void put(Artifact artifact) {
        artifacts.putIfAbsent(artifact.hash(), artifact);
    }

    @Override
    public Optional<Artifact> get(String hash) {
        return Optional.ofNullable(artifacts.get(hash));
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
