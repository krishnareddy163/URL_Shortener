package com.example.agentic.core.gate;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.workspace.Diff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** R10: build tools run with a timeout, in the staging dir, with an allowlisted environment. */
class BuildRunnerTest {

    @TempDir
    Path dir;

    @Test
    void environmentIsStrippedToTheAllowlist() throws Exception {
        Path tool = script("env; exit 3");
        BuildRunner runner = new BuildRunner(new PolicyConfig.Build(tool.toString(), 10, "-Xmx64m", List.of("PATH", "HOME"), null));

        BuildRunner.BuildResult result = runner.run(dir, List.of(), dir.resolve("logs/env.log"));

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.output()).contains("MAVEN_OPTS=-Xmx64m").contains("PATH=");
        assertThat(result.output().lines().map(line -> line.split("=", 2)[0]))
                .allMatch(name -> Set.of("PATH", "HOME", "MAVEN_OPTS", "PWD", "SHLVL", "_", "OLDPWD").contains(name));
    }

    @Test
    void timeoutKillsTheProcessAndYieldsTheTimeoutSignature() throws Exception {
        Path tool = script("sleep 30");
        BuildRunner runner = new BuildRunner(new PolicyConfig.Build(tool.toString(), 1, "", List.of("PATH"), null));
        MavenGate gate = new MavenGate("compile", "compile", runner);

        long start = System.nanoTime();
        GateResult result = gate.evaluate(context());

        assertThat((System.nanoTime() - start) / 1_000_000_000L).isLessThan(15);
        assertThat(result).isInstanceOf(GateResult.Fail.class);
        assertThat(((GateResult.Fail) result).signature()).isEqualTo("timeout");
    }

    @Test
    void failureOutputIsCondensedIntoFeedback() throws Exception {
        Path tool = script("echo 'noise line'; echo '[ERROR] Tests run: 1, Failures: 1'; "
                + "echo '[ERROR]   CodeGeneratorTest.length:12 expected: <8> but was: <7>'; exit 1");
        MavenGate gate = new MavenGate("unit-tests", "test", new BuildRunner(new PolicyConfig.Build(tool.toString(), 10, "", List.of("PATH"), null)));

        GateResult.Fail failure = (GateResult.Fail) gate.evaluate(context());

        assertThat(failure.reason()).contains("expected: <8> but was: <7>").doesNotContain("noise line");
    }

    @Test
    void successfulBuildPasses() throws Exception {
        Path tool = script("exit 0");
        MavenGate gate = new MavenGate("compile", "compile", new BuildRunner(new PolicyConfig.Build(tool.toString(), 10, "", List.of("PATH"), null)));
        assertThat(gate.evaluate(context())).isEqualTo(GateResult.pass());
    }

    private GateContext context() {
        Node node = new Node("n", "developer", Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null);
        return new GateContext(node, "developer", new Proposal(Map.of(), "r", List.of(), Map.of()), dir, Diff.empty(),
                Map.of(), dir, dir.resolve("logs"));
    }

    @Test
    void theCoverageReportIsHandedBackOnlyWhenAsked() throws Exception {
        Path tool = script("mkdir -p target/site/jacoco && printf 'GROUP,PACKAGE,CLASS\\n' > " + BuildRunner.COVERAGE_REPORT);
        BuildRunner runner = new BuildRunner(new PolicyConfig.Build(tool.toString(), 10, "", List.of("PATH"), null));

        BuildRunner.BuildResult asked = runner.run(dir, List.of(), dir.resolve("logs/asked.log"), true);
        BuildRunner.BuildResult notAsked = runner.run(dir, List.of(), dir.resolve("logs/plain.log"));

        assertThat(asked.succeeded()).isTrue();
        assertThat(asked.output()).contains(BuildRunner.REPORT_MARKER + "\nGROUP,PACKAGE,CLASS");
        assertThat(notAsked.output()).doesNotContain(BuildRunner.REPORT_MARKER);
    }

    private Path script(String body) throws Exception {
        Path file = dir.resolve("tool-" + System.nanoTime() + ".sh");
        Files.writeString(file, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }
}
