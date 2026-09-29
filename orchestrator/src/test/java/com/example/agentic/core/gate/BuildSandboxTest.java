package com.example.agentic.core.gate;

import com.example.agentic.core.policy.PolicyConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R10 with a container: generated code builds with no network, a read-only root and a read-only Maven cache. */
class BuildSandboxTest {
    private static final String IMAGE = "maven:3.9-eclipse-temurin-25";

    @TempDir
    Path dir;

    private static BuildRunner runner(String command, PolicyConfig.SandboxMode mode, String image, String repository) {
        return runner(command, mode, image, repository, false);
    }

    private static BuildRunner runner(String command, PolicyConfig.SandboxMode mode, String image, String repository,
                                      boolean insideContainer) {
        return new BuildRunner(new PolicyConfig.Build(command, 30, "-Xmx256m", List.of("PATH"),
                new PolicyConfig.Sandbox(mode, image, "512m", "1", 128, repository)), insideContainer);
    }

    @Test
    void onlyTheFixedCoverageReportCanLeaveTheContainer() throws Exception {
        BuildRunner runner = runner("mvn", PolicyConfig.SandboxMode.DOCKER, IMAGE, Files.createDirectories(dir.resolve("r")).toString());

        String withReport = String.join(" ", runner.dockerCommand("agentic-gate-x", List.of("test"), true));
        String plain = String.join(" ", runner.dockerCommand("agentic-gate-x", List.of("test")));

        assertThat(withReport).contains("cat " + BuildRunner.COVERAGE_REPORT, "echo '" + BuildRunner.REPORT_MARKER + "'",
                "exit $code").doesNotContain("exec \"$@\"");
        assertThat(plain).contains("exec \"$@\"").doesNotContain(BuildRunner.REPORT_MARKER);
    }

    @Test
    void containerCommandIsolatesTheBuild() throws Exception {
        Path repository = Files.createDirectories(dir.resolve("repo"));

        List<String> command = runner("mvn", PolicyConfig.SandboxMode.DOCKER, IMAGE, repository.toString())
                .dockerCommand("agentic-gate-x", List.of("-q", "-B", "test"));

        assertThat(String.join(" ", command))
                .contains("--network none").contains("--read-only").contains("--cap-drop ALL")
                .contains("--user 65534:65534").contains("--security-opt no-new-privileges")
                .contains("--memory 512m").contains("--pids-limit 128")
                .contains("--tmpfs /work:rw,exec,size=2g,mode=1777")
                .contains("type=bind,src=" + repository + ",dst=/m2-ro,readonly")
                .contains("mvn -q -B test -o -Dmaven.repo.local=/tmp/m2 -Dmaven.repo.local.tail=/m2-ro")
                .doesNotContain("dst=/work").doesNotContain("ANTHROPIC");
    }

    @Test
    void requiredSandboxFailsClosedWhenUnavailable() {
        BuildRunner runner = runner("mvn", PolicyConfig.SandboxMode.DOCKER, "agentic/no-such-image:0", dir.toString());

        BuildRunner.BuildResult result = runner.run(dir, List.of("test"), dir.resolve("logs/b.log"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.output()).startsWith("build sandbox required by policy but unavailable");
        assertThat(runner.describe()).contains("will FAIL");
    }

    @Test
    void autoFallsBackToTheHostWhenTheSandboxIsUnavailable() {
        BuildRunner runner = runner("mvn", PolicyConfig.SandboxMode.AUTO, "agentic/no-such-image:0", dir.toString());
        assertThat(runner.sandboxed()).isFalse();
        assertThat(runner.describe()).startsWith("build gates run on the host (container sandbox unavailable");
        assertThat(runner("mvn", PolicyConfig.SandboxMode.AUTO, "agentic/no-such-image:0", dir.toString(), true).describe())
                .as("inside this project's image the gates are already containerized")
                .isEqualTo("build gates run directly inside this container (no nested sandbox)");
    }

    @Test
    void onlyTheMountVisibilityRaceIsRetried() {
        assertThat(BuildRunner.mountNotVisibleYet(new BuildRunner.BuildResult(125, false,
                "docker: Error response from daemon: invalid mount config for type \"bind\": bind source path does not exist: /x")))
                .isTrue();
        assertThat(BuildRunner.mountNotVisibleYet(new BuildRunner.BuildResult(125, false, "docker: no such image"))).isFalse();
        assertThat(BuildRunner.mountNotVisibleYet(new BuildRunner.BuildResult(1, false, "bind source path does not exist")))
                .isFalse();
    }

    @Test
    void aKilledBuildSaysSo() {
        assertThat(MavenGate.killedHint(137)).contains("killed").contains("build.sandbox.memory");
        assertThat(MavenGate.killedHint(1)).isEmpty();
    }

    @Test
    void unsafeSandboxSettingsAreRejected() {
        assertThatThrownBy(() -> runner("mvn", PolicyConfig.SandboxMode.DOCKER, "maven; rm -rf /", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sandbox image");
    }

    @Test
    void realContainerBuildsAPrivateCopyWithNoNetworkAndNoHostWrites() throws Exception {
        BuildRunner runner = runner("sh", PolicyConfig.SandboxMode.AUTO, IMAGE, "");
        Assumptions.assumeTrue(runner.sandboxed(), "needs a local docker with " + IMAGE);
        Path staging = Files.createDirectories(dir.resolve("staging"));
        Files.writeString(staging.resolve("Source.java"), "class Source {}\n");

        BuildRunner.BuildResult result = runner.run(staging, List.of("-c",
                "cat Source.java; echo tampered > Source.java; echo built > out.txt;"
                        + " (curl -s -m 3 https://repo.maven.apache.org > /dev/null && echo NETWORK-OPEN) || echo network-blocked;"
                        + " (touch /usr/escape 2> /dev/null && echo ROOT-WRITABLE) || echo root-read-only;"
                        + " (touch /m2-ro/escape 2> /dev/null && echo CACHE-WRITABLE) || echo cache-read-only"),
                dir.resolve("logs/sandbox.log"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("class Source {}", "network-blocked", "root-read-only", "cache-read-only")
                .doesNotContain("NETWORK-OPEN", "ROOT-WRITABLE", "CACHE-WRITABLE");
        assertThat(Files.readString(staging.resolve("Source.java"))).as("staged files cannot be changed by the build")
                .isEqualTo("class Source {}\n");
        assertThat(staging.resolve("out.txt")).doesNotExist();
    }
}
