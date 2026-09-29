package com.example.agentic.core.gate;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.workspace.Diff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Positive and negative cases for the gates that demand evidence: review, design, coverage and functional coverage. */
class EvidenceGatesTest {
    private static final Map<String, Artifact> UPSTREAM = Map.of(
            "implement", Artifact.of("implement", List.of(), "i", Map.of("src/main/A.java", "class A {}"), Map.of()),
            "unit_tests", Artifact.of("unit_tests", List.of(), "t", Map.of("src/test/ATest.java", "class ATest {}"), Map.of()));

    @TempDir
    Path staging;

    @Test
    void reviewMustCoverEverySubmittedFileAndResolveEveryFinding() {
        ReviewCompleteGate gate = new ReviewCompleteGate();
        Map<String, Object> finding = Map.of("severity", "LOW", "file", "src/main/A.java", "message", "nit",
                "status", "DEFERRED", "resolution", "next release");
        List<String> all = List.of("src/main/A.java", "src/test/ATest.java");

        assertThat(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(finding), "recommendation", "GO"))))
                .isEqualTo(GateResult.pass());
        assertThat(reason(gate.evaluate(review(Map.of("reviewedFiles", List.of("src/main/A.java"), "findings", List.of(),
                "recommendation", "GO"))))).isEqualTo("1 submitted file(s) not reviewed: src/test/ATest.java");
        assertThat(reason(gate.evaluate(review(Map.of("findings", List.of(), "recommendation", "GO")))))
                .contains("reviewedFiles");
        assertThat(reason(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(Map.of("severity", "LOW",
                "file", "a", "message", "m")), "recommendation", "GO"))))).contains("status");
        assertThat(reason(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(Map.of("severity", "LOW",
                "file", "a", "message", "m", "status", "FIXED", "resolution", " ")), "recommendation", "GO"))))).contains("resolution");
        assertThat(reason(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(Map.of("severity", "HIGH",
                "file", "a", "message", "sql injection", "status", "DEFERRED", "resolution", "later")), "recommendation", "GO")))))
                .isEqualTo("a HIGH or CRITICAL finding that is not FIXED requires NO_GO");
        assertThat(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(Map.of("severity", "HIGH",
                "file", "a", "message", "sql injection", "status", "FIXED", "resolution", "parameterized")), "recommendation", "GO"))))
                .as("a fixed HIGH finding may go").isEqualTo(GateResult.pass());
        assertThat(reason(gate.evaluate(review(Map.of("reviewedFiles", all, "findings", List.of(), "recommendation", "MAYBE")))))
                .contains("GO or NO_GO");
    }

    @Test
    void everyDesignDocumentNeedsAMermaidDiagram() {
        DesignDiagramsGate gate = new DesignDiagramsGate();

        assertThat(gate.evaluate(files(Map.of("docs/design.md", "# D\n```mermaid\nflowchart LR\n```\n", "openapi.yaml", "x"))))
                .isEqualTo(GateResult.pass());
        assertThat(reason(gate.evaluate(files(Map.of("docs/design-expiry.md", "# D, no picture")))))
                .isEqualTo("docs/design-expiry.md has no Mermaid diagram (a ```mermaid block)");
        assertThat(reason(gate.evaluate(files(Map.of("openapi.yaml", "x"))))).contains("no design document");
        assertThat(DesignDiagramsGate.isDesignDocument("docs/design-security.md")).isTrue();
        assertThat(DesignDiagramsGate.isDesignDocument("docs/adr/design.md")).isFalse();
        assertThat(DesignDiagramsGate.isDesignDocument("docs/design.txt")).isFalse();
    }

    @Test
    void coverageIsParsedFromTheJacocoCsv() {
        String output = "[INFO] build noise\n" + BuildRunner.REPORT_MARKER + "\n"
                + "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,"
                + "LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED\n"
                + "app,com.example,Full,0,40,0,6,0,12,0,4,0,3\n"
                + "app,com.example,Launcher,5,0,0,0,2,0,1,0,1,0\n";

        List<TestCoverageGate.ClassCoverage> classes = TestCoverageGate.parse(output).orElseThrow();

        assertThat(classes).containsExactly(new TestCoverageGate.ClassCoverage("com.example.Full", 0, 12, 0, 6),
                new TestCoverageGate.ClassCoverage("com.example.Launcher", 2, 0, 0, 0));
        assertThat(TestCoverageGate.parse("no report here")).isEmpty();
        assertThat(TestCoverageGate.parse(BuildRunner.REPORT_MARKER + "\nUNEXPECTED,HEADER\n")).isEmpty();
    }

    @Test
    void everyGapBelowTargetMustBeDocumentedAndIsNamed() {
        List<TestCoverageGate.ClassCoverage> classes = List.of(new TestCoverageGate.ClassCoverage("com.example.Full", 0, 12, 0, 6),
                new TestCoverageGate.ClassCoverage("com.example.Launcher", 2, 0, 0, 0));
        TestCoverageGate documented = gate(new PolicyConfig.Coverage(100, 80, 100, Map.of("com.example.Launcher", "main() only")));
        TestCoverageGate undocumented = gate(new PolicyConfig.Coverage(100, 80, 100, Map.of()));
        TestCoverageGate strict = gate(PolicyConfig.Coverage.strict());

        GateResult pass = documented.judge(classes);
        assertThat(pass).isInstanceOf(GateResult.Pass.class);
        assertThat(((GateResult.Pass) pass).detail())
                .isEqualTo("line 85.7% (12/14), branch 100.0% (6/6), target 100% over 2 classes; below target (documented): "
                        + "com.example.Launcher (line 0.0%, branch 100.0%): main() only");
        assertThat(reason(undocumented.judge(classes))).contains("below target without a documented exception: com.example.Launcher");
        assertThat(reason(strict.judge(classes))).contains("below the minimum (line 100.0%, branch 100.0%)");
        assertThat(((GateResult.Pass) strict.judge(classes.subList(0, 1))).detail()).endsWith("; every class at target");
    }

    @Test
    void functionalCoverageMapsEveryCriterionToExistingTests() throws Exception {
        Files.createDirectories(staging.resolve("src/test/java/app"));
        Files.writeString(staging.resolve("src/test/java/app/LinkTest.java"), "class LinkTest { @Test void creates() {} void redirects( ) {} }");
        Map<String, Artifact> upstream = Map.of("requirements", Artifact.of("requirements", List.of(), "r", Map.of(),
                Map.of("acceptanceCriteria", List.of("create returns 201", "redirect returns 302"))));
        FunctionalCoverageGate gate = new FunctionalCoverageGate();

        GateResult pass = gate.evaluate(qa(upstream, List.of(
                Map.of("criterion", "create returns 201", "tests", List.of("LinkTest#creates")),
                Map.of("criterion", "redirect returns 302", "tests", List.of("LinkTest#redirects", "LinkTest#creates")))));
        assertThat(((GateResult.Pass) pass).detail()).isEqualTo("2 of 2 acceptance criteria mapped to 2 existing tests");
        assertThat(reason(gate.evaluate(qa(upstream, List.of(Map.of("criterion", "create returns 201", "tests", List.of("LinkTest#creates")))))))
                .isEqualTo("1 acceptance criterion/criteria not mapped to any test: redirect returns 302");
        assertThat(reason(gate.evaluate(qa(upstream, List.of(
                Map.of("criterion", "create returns 201", "tests", List.of("LinkTest#creates")),
                Map.of("criterion", "redirect returns 302", "tests", List.of("LinkTest#invented")))))))
                .isEqualTo("referenced test does not exist: LinkTest#invented");
        assertThat(reason(gate.evaluate(qa(upstream, List.of(Map.of("criterion", "create returns 201", "tests", List.of("not a test")))))))
                .contains("ClassName#method");
        assertThat(reason(gate.evaluate(qa(Map.of(), List.of())))).contains("no upstream acceptance criteria");
    }

    private GateContext qa(Map<String, Artifact> upstream, List<Map<String, Object>> matrix) {
        Proposal proposal = new Proposal(Map.of(), "qa", List.of("requirements"), Map.of("functionalCoverage", matrix));
        return new GateContext(node("tester"), "tester", proposal, staging, Diff.empty(), upstream, staging, staging);
    }

    private static TestCoverageGate gate(PolicyConfig.Coverage policy) {
        return new TestCoverageGate(new BuildRunner(new PolicyConfig.Build("mvn", 10, "", List.of("PATH"), null)), policy);
    }

    private static GateContext review(Map<String, Object> data) {
        return new GateContext(node("reviewer"), "reviewer", new Proposal(Map.of(), "r", List.copyOf(UPSTREAM.keySet()), data),
                null, Diff.empty(), UPSTREAM, Path.of("."), Path.of("."));
    }

    private static GateContext files(Map<String, String> files) {
        return new GateContext(node("architect"), "architect", new Proposal(files, "d", List.of("requirements"), Map.of()),
                null, Diff.empty(), Map.of(), Path.of("."), Path.of("."));
    }

    private static Node node(String agent) {
        return new Node("n", agent, Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null);
    }

    private static String reason(GateResult result) {
        assertThat(result).isInstanceOf(GateResult.Fail.class);
        return ((GateResult.Fail) result).reason();
    }
}
