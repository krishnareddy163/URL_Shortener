package com.example.agentic.core.report;

import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.TypedEvent;

import java.util.List;

/**
 * Everything a report section reads: the log, its fold, and the artifact store.
 *
 * @param runId     run identifier
 * @param events    the run's events in order
 * @param state     fold of {@code events}
 * @param artifacts content-addressed artifacts
 */
record ReportInput(String runId, List<Event> events, RunState state, ArtifactStore artifacts) {

    ReportInput {
        events = List.copyOf(events);
    }

    static ReportInput of(String runId, List<Event> events, ArtifactStore artifacts) {
        return new ReportInput(runId, events, RunState.fold(events), artifacts);
    }

    <T extends Payload> List<TypedEvent<T>> events(Class<T> type) {
        return TypedEvent.of(events, type);
    }
}
