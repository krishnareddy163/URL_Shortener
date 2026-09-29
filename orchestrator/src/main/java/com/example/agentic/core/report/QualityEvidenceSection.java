package com.example.agentic.core.report;

import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.TypedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.example.agentic.core.report.MarkdownText.cell;

/**
 * The evidence each role produced, from the current artifacts and gate results: user stories and acceptance
 * criteria, design diagrams, measured test coverage, the functional coverage matrix, and code review coverage with
 * every finding's status and resolution, and the workspace git commit of every promotion.
 */
final class QualityEvidenceSection implements ReportSection {
    private static final String DIAGRAM = "```mermaid";

    @Override
    public void render(ReportInput input, StringBuilder out) {
        Map<String, Artifact> current = currentArtifacts(input);
        out.append("## Quality evidence\n\n");
        requirements(current, out);
        designs(current, out);
        coverage(input, out);
        functionalCoverage(current, out);
        reviews(input, current, out);
        gitHistory(input, out);
    }

    private static void gitHistory(ReportInput input, StringBuilder out) {
        StringBuilder rows = new StringBuilder();
        for (TypedEvent<Payload.NodeDone> done : input.events(Payload.NodeDone.class)) {
            if (done.payload().commit() != null) {
                rows.append("| ").append(done.event().seq()).append(" | `").append(done.event().nodeId()).append("` | `")
                        .append(done.payload().commit(), 0, Math.min(12, done.payload().commit().length())).append("` | ")
                        .append(done.payload().files().size()).append(" | ")
                        .append(done.payload().approvedBy() == null ? "-" : done.payload().approvedBy()).append(" |\n");
            }
        }
        if (!rows.isEmpty()) {
            out.append("### Workspace git history\n\nEach promotion is one commit in the run's workspace (`git log` there shows "
                    + "the rationale and trailers).\n\n| Seq | Node | Commit | Files | Approved by |\n|---|---|---|---:|---|\n")
                    .append(rows).append('\n');
        }
    }

    private static Map<String, Artifact> currentArtifacts(ReportInput input) {
        Map<String, Artifact> current = new LinkedHashMap<>();
        RunState state = input.state();
        if (state.graph() == null) {
            return current;
        }
        for (String id : state.graph().topologicalOrder()) {
            String hash = state.node(id).currentHash();
            Optional<Artifact> artifact = hash == null ? Optional.empty() : input.artifacts().get(hash);
            artifact.ifPresent(found -> current.put(id, found));
        }
        return current;
    }

    private static void requirements(Map<String, Artifact> current, StringBuilder out) {
        current.forEach((node, artifact) -> {
            if (artifact.data().get("userStories") instanceof List<?> stories) {
                out.append("### User stories (`").append(node).append("`)\n\n");
                stories.forEach(story -> out.append("- ").append(cell(String.valueOf(story), 400)).append('\n'));
                if (artifact.data().get("acceptanceCriteria") instanceof List<?> criteria) {
                    out.append("\nAcceptance criteria: ").append(criteria.size()).append(".\n");
                }
                out.append('\n');
            }
        });
    }

    private static void designs(Map<String, Artifact> current, StringBuilder out) {
        StringBuilder rows = new StringBuilder();
        current.forEach((node, artifact) -> artifact.files().forEach((path, content) -> {
            if (path.startsWith("docs/design") && path.endsWith(".md")) {
                rows.append("| `").append(node).append("` | `").append(path).append("` | ")
                        .append(content.split(DIAGRAM, -1).length - 1).append(" |\n");
            }
        }));
        if (!rows.isEmpty()) {
            out.append("### Design documents\n\n| Node | Document | Mermaid diagrams |\n|---|---|---:|\n").append(rows).append('\n');
        }
    }

    private static void coverage(ReportInput input, StringBuilder out) {
        Map<String, String> latest = new LinkedHashMap<>();
        for (TypedEvent<Payload.GatePassed> passed : input.events(Payload.GatePassed.class)) {
            if (passed.payload().detail() != null) {
                latest.put(passed.event().nodeId() + " / " + passed.payload().gate(), passed.payload().detail());
            }
        }
        if (!latest.isEmpty()) {
            out.append("### Measured evidence from gates\n\n| Node / gate | Result |\n|---|---|\n");
            latest.forEach((key, detail) -> out.append("| `").append(key).append("` | ").append(cell(detail, 2000)).append(" |\n"));
            out.append('\n');
        }
    }

    private static void functionalCoverage(Map<String, Artifact> current, StringBuilder out) {
        current.forEach((node, artifact) -> {
            if (artifact.data().get("functionalCoverage") instanceof List<?> matrix) {
                out.append("### Functional coverage (`").append(node).append("`)\n\n| Acceptance criterion | Tests |\n|---|---|\n");
                for (Object row : matrix) {
                    if (row instanceof Map<?, ?> entry) {
                        out.append("| ").append(cell(String.valueOf(entry.get("criterion")), 400)).append(" | ")
                                .append(cell(String.valueOf(entry.get("tests")), 800)).append(" |\n");
                    }
                }
                out.append('\n');
            }
        });
    }

    private static void reviews(ReportInput input, Map<String, Artifact> current, StringBuilder out) {
        current.forEach((node, artifact) -> {
            if (!artifact.data().containsKey("recommendation")) {
                return;
            }
            Set<String> submitted = new TreeSet<>();
            input.state().graph().upstreamOf(node).forEach(upstream -> {
                Artifact upstreamArtifact = current.get(upstream);
                if (upstreamArtifact != null) {
                    submitted.addAll(upstreamArtifact.files().keySet());
                }
            });
            Set<String> reviewed = new TreeSet<>();
            if (artifact.data().get("reviewedFiles") instanceof List<?> list) {
                list.forEach(file -> reviewed.add(String.valueOf(file)));
            }
            long covered = submitted.stream().filter(reviewed::contains).count();
            out.append("### Code review (`").append(node).append("`): ").append(artifact.data().get("recommendation"))
                    .append("\n\nReviewed ").append(covered).append(" of ").append(submitted.size())
                    .append(" submitted files.\n\n");
            if (artifact.data().get("findings") instanceof List<?> findings && !findings.isEmpty()) {
                out.append("| Severity | File | Finding | Status | Resolution |\n|---|---|---|---|---|\n");
                for (Object item : findings) {
                    if (item instanceof Map<?, ?> finding) {
                        out.append("| ").append(finding.get("severity")).append(" | `").append(finding.get("file")).append("` | ")
                                .append(cell(String.valueOf(finding.get("message")))).append(" | ").append(finding.get("status"))
                                .append(" | ").append(cell(String.valueOf(finding.get("resolution")))).append(" |\n");
                    }
                }
                out.append('\n');
            } else {
                out.append("No findings.\n\n");
            }
        });
    }
}
