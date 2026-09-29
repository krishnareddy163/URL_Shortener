package com.example.agentic.scenarios;

import com.example.agentic.cli.OrchestratorCli;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.state.SqliteEventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 9.4, driven through the CLI with inline fixtures: every attempt fails the real forbidden-api gate
 * with the same error, the circuit breaker trips, the fallback agent gets one round, and the run safe-stops
 * with exit code 20, SKIPPED downstream nodes and an incident report.
 */
class ResilienceScenarioTest {
    private static final String EXEC = """
            package app;

            class Shell {
                void run(String cmd) throws Exception {
                    Runtime.getRuntime().exec(cmd);
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void identicalFailuresTripTheBreakerFallBackOnceAndSafeStop() throws Exception {
        Path scenario = dir.resolve("resilience");
        Files.createDirectories(scenario);
        Files.writeString(scenario.resolve("workflow.yaml"), """
                name: resilience
                requirement: Add a shell helper.
                nodes:
                  - id: build
                    agent: developer
                    fallbackAgent: tester
                    maxRetries: 3
                    exitGates: [artifact-metadata, forbidden-api]
                  - id: verify
                    agent: tester
                    dependsOn: [build]
                """);
        for (int attempt = 1; attempt <= 4; attempt++) {
            fixture(scenario.resolve("fixtures/build/attempt" + attempt), "src/main/java/app/Shell.java");
        }
        fixture(scenario.resolve("fixtures/build/fallback/attempt1"), "src/main/java/app/Shell.java");
        fixture(scenario.resolve("fixtures/build/fallback/attempt2"), "src/main/java/app/Shell.java");

        StringWriter out = new StringWriter();
        CommandLine cli = OrchestratorCli.commandLine();
        cli.setOut(new PrintWriter(out));
        int exit = cli.execute("run", scenario.resolve("workflow.yaml").toString(), "--run-id", "resilience",
                "--runs-dir", dir.resolve("runs").toString(),
                "--policies", ScenarioHarness.repoRoot().resolve("policies/policies.yaml").toString(), "--no-color");

        assertThat(exit).isEqualTo(20);
        assertThat(out.toString()).contains("SAFE-STOPPED");
        Path runDir = dir.resolve("runs/resilience");
        try (SqliteEventStore store = new SqliteEventStore(runDir.resolve("events.db"))) {
            List<Event> events = store.read("resilience");
            RunState state = RunState.fold(events);
            List<Event> calls = events.stream().filter(e -> e.type() == EventType.AGENT_CALLED).toList();
            assertThat(calls).extracting(e -> e.payload(Payload.AgentCalled.class).agent()).containsExactly("developer", "developer", "tester", "tester");
            assertThat(events.stream().filter(e -> e.type() == EventType.FALLBACK)).hasSize(1);
            assertThat(events.stream().filter(e -> e.type() == EventType.FALLBACK).findFirst().orElseThrow()
                    .payload(Payload.Fallback.class).cause())
                    .contains("circuit breaker");
            assertThat(events.stream().filter(e -> e.type() == EventType.NODE_FAILED).findFirst().orElseThrow()
                    .payload(Payload.NodeFailed.class).breakerTripped()).isTrue();
            assertThat(state.status("build")).isEqualTo(NodeStatus.FAILED);
            assertThat(state.status("verify")).isEqualTo(NodeStatus.SKIPPED);
            assertThat(Files.exists(runDir.resolve("workspace/src/main/java/app/Shell.java"))).isFalse();
        }
        assertThat(Files.readString(runDir.resolve("incident.md"))).contains("circuit breaker tripped")
                .contains("forbidden-api").contains("Runtime").contains("FALLBACK developer → tester");
        assertThat(runDir.resolve("report.md")).exists();
    }

    @Test
    void invalidWorkflowsAreRejectedAtLoadWithExitCode2() throws Exception {
        Path workflow = dir.resolve("bad.yaml");
        Files.writeString(workflow, """
                name: bad
                requirement: r
                nodes:
                  - {id: a, agent: developer, dependsOn: [b]}
                  - {id: b, agent: developer, dependsOn: [a], exitGates: [no-such-gate]}
                """);
        StringWriter err = new StringWriter();
        CommandLine cli = OrchestratorCli.commandLine();
        cli.setErr(new PrintWriter(err));

        int exit = cli.execute("run", workflow.toString(), "--runs-dir", dir.resolve("runs").toString(),
                "--policies", ScenarioHarness.repoRoot().resolve("policies/policies.yaml").toString());

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).contains("invalid workflow").contains("dependency cycle").contains("unknown exit gate 'no-such-gate'");
        assertThat(dir.resolve("runs")).doesNotExist();
    }

    @Test
    void approveRequiresByAndUnknownRunsAreUsageErrors() {
        CommandLine cli = OrchestratorCli.commandLine();
        cli.setErr(new PrintWriter(new StringWriter()));
        assertThat(cli.execute("approve", "some-run", "design", "--comment", "ok")).isEqualTo(2);
        assertThat(cli.execute("status", "missing-run", "--runs-dir", dir.toString())).isEqualTo(2);
    }

    private static void fixture(Path attemptDir, String file) throws Exception {
        Files.createDirectories(attemptDir.resolve(file).getParent());
        Files.writeString(attemptDir.resolve(file), EXEC);
        Files.writeString(attemptDir.resolve("proposal.json"),
                "{\"rationale\": \"shell helper\", \"derivedFrom\": [\"requirement\"], \"data\": {}}");
    }
}
