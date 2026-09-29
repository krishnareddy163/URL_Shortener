package com.example.agentic.core.gate;

import com.example.agentic.core.policy.PolicyConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures test coverage of the staged project with JaCoCo ({@code mvn -q -B test jacoco:report}) and checks it
 * against {@link PolicyConfig.Coverage}. The pass detail, recorded in the audit log and the report, gives overall
 * line and branch coverage and names every class below the target, so a gap is always visible. The gate fails when
 * the tests fail, when overall coverage drops below the policy minimums, or when a class that is not a documented
 * exception misses the target.
 */
public final class TestCoverageGate implements Gate {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    private static final List<String> COLUMNS = List.of("PACKAGE", "CLASS", "BRANCH_MISSED", "BRANCH_COVERED",
            "LINE_MISSED", "LINE_COVERED");

    private final BuildRunner runner;
    private final PolicyConfig.Coverage policy;

    public TestCoverageGate(BuildRunner runner, PolicyConfig.Coverage policy) {
        this.runner = runner;
        this.policy = policy;
    }

    @Override
    public String id() {
        return "test-coverage";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.staging() == null) {
            return GateResult.fail(id(), "test-coverage is an exit gate and needs a staged proposal");
        }
        String logName = context.node().id() + "-" + id() + "-" + INVOCATIONS.incrementAndGet() + ".log";
        BuildRunner.BuildResult result = runner.run(context.staging(), List.of("-q", "-B", "test", "jacoco:report"),
                context.logDir().resolve(logName), true);
        if (result.timedOut()) {
            return new GateResult.Fail(id(), "coverage build timed out after " + runner.timeoutSeconds() + " s", "timeout");
        }
        if (!result.succeeded()) {
            return GateResult.fail(id(), "tests failed while measuring coverage (exit " + result.exitCode() + "):\n"
                    + MavenGate.condense(result.output()));
        }
        Optional<List<ClassCoverage>> classes = parse(result.output());
        if (classes.isEmpty() || classes.get().isEmpty()) {
            return GateResult.fail(id(), "no JaCoCo report was produced (is the jacoco-maven-plugin configured?)");
        }
        return judge(classes.get());
    }

    /**
     * Coverage counters of one class.
     *
     * @param name          fully qualified class name
     * @param lineMissed    lines not executed
     * @param lineCovered   lines executed
     * @param branchMissed  branches not taken
     * @param branchCovered branches taken
     */
    record ClassCoverage(String name, int lineMissed, int lineCovered, int branchMissed, int branchCovered) {
        double linePercent() {
            return percent(lineCovered, lineMissed);
        }

        double branchPercent() {
            return percent(branchCovered, branchMissed);
        }
    }

    GateResult judge(List<ClassCoverage> classes) {
        int lineMissed = classes.stream().mapToInt(ClassCoverage::lineMissed).sum();
        int lineCovered = classes.stream().mapToInt(ClassCoverage::lineCovered).sum();
        int branchMissed = classes.stream().mapToInt(ClassCoverage::branchMissed).sum();
        int branchCovered = classes.stream().mapToInt(ClassCoverage::branchCovered).sum();
        double line = percent(lineCovered, lineMissed);
        double branch = percent(branchCovered, branchMissed);
        List<String> belowTarget = new ArrayList<>();
        List<String> unexplained = new ArrayList<>();
        for (ClassCoverage coverage : classes) {
            if (coverage.linePercent() < policy.target() || coverage.branchPercent() < policy.target()) {
                String reason = policy.exceptions().get(coverage.name());
                String entry = String.format(Locale.ROOT, "%s (line %.1f%%, branch %.1f%%)%s", coverage.name(),
                        coverage.linePercent(), coverage.branchPercent(), reason == null ? "" : ": " + reason);
                belowTarget.add(entry);
                if (reason == null) {
                    unexplained.add(entry);
                }
            }
        }
        String summary = String.format(Locale.ROOT, "line %.1f%% (%d/%d), branch %.1f%% (%d/%d), target %.0f%% over %d classes",
                line, lineCovered, lineCovered + lineMissed, branch, branchCovered, branchCovered + branchMissed,
                policy.target(), classes.size());
        if (line < policy.minimumLine() || branch < policy.minimumBranch()) {
            return GateResult.fail(id(), summary + String.format(Locale.ROOT, "; below the minimum (line %.1f%%, branch %.1f%%)",
                    policy.minimumLine(), policy.minimumBranch()));
        }
        if (!unexplained.isEmpty()) {
            return GateResult.fail(id(), summary + "; below target without a documented exception: "
                    + String.join("; ", unexplained));
        }
        return GateResult.pass(summary + (belowTarget.isEmpty() ? "; every class at target"
                : "; below target (documented): " + String.join("; ", belowTarget)));
    }

    /** Reads the JaCoCo CSV that follows {@link BuildRunner#REPORT_MARKER}; empty if there is none. */
    static Optional<List<ClassCoverage>> parse(String output) {
        int marker = output.lastIndexOf(BuildRunner.REPORT_MARKER);
        if (marker < 0) {
            return Optional.empty();
        }
        List<String> lines = output.substring(marker + BuildRunner.REPORT_MARKER.length()).strip().lines().toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        List<String> header = Arrays.asList(lines.getFirst().split(","));
        if (!header.containsAll(COLUMNS)) {
            return Optional.empty();
        }
        List<ClassCoverage> classes = new ArrayList<>();
        for (String row : lines.subList(1, lines.size())) {
            String[] cells = row.split(",");
            if (cells.length != header.size()) {
                continue;
            }
            classes.add(new ClassCoverage(cells[header.indexOf("PACKAGE")] + "." + cells[header.indexOf("CLASS")],
                    Integer.parseInt(cells[header.indexOf("LINE_MISSED")]), Integer.parseInt(cells[header.indexOf("LINE_COVERED")]),
                    Integer.parseInt(cells[header.indexOf("BRANCH_MISSED")]),
                    Integer.parseInt(cells[header.indexOf("BRANCH_COVERED")])));
        }
        return Optional.of(classes);
    }

    private static double percent(int covered, int missed) {
        int total = covered + missed;
        return total == 0 ? 100.0 : 100.0 * covered / total;
    }
}
