package com.example.agentic.core.report;

import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates {@code report.md} purely from the event log and artifact store, one {@link ReportSection} after
 * another: run summary, clarifications and assumptions, Mermaid graph (with before/after when the plan
 * changed), timeline, metrics, approvals, decision lineage, policy and gate results, invalidation and replan
 * history.
 */
public final class ReportWriter {
    private static final List<ReportSection> SECTIONS = List.of(new SummarySection(), new ClarificationsSection(),
            new GraphSection(), new TimelineSection(), new MetricsSection(), new ApprovalsSection(),
            new LineageSection(), new QualityEvidenceSection(), new GateResultsSection(), new ReplanHistorySection());

    private ReportWriter() {
    }

    public static String render(String runId, List<Event> events, ArtifactStore artifacts) {
        ReportInput input = ReportInput.of(runId, events, artifacts);
        StringBuilder out = new StringBuilder();
        SECTIONS.forEach(section -> section.render(input, out));
        return out.toString();
    }

    public static Path write(Path runDir, String runId, List<Event> events, ArtifactStore artifacts) throws IOException {
        Path file = runDir.resolve("report.md");
        Files.writeString(file, render(runId, events, artifacts));
        return file;
    }
}
