package com.example.agentic.core.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Build gates that run real Maven in the staging directory: {@code compile} ({@code mvn -q -B compile}),
 * {@code unit-tests} and {@code regression-tests} ({@code mvn -q -B test}; the regression gate is used
 * where the existing suite must keep passing). Failure output is condensed to at most 4,000 characters,
 * which becomes the agent's feedback for the next attempt.
 */
public final class MavenGate implements Gate {
    static final int MAX_FEEDBACK_CHARS = 4_000;
    private static final int KILLED = 137;
    private static final Pattern SALIENT = Pattern.compile(
            "\\[ERROR]|FAIL|Tests run:|[Ee]xpect(?:ed|ing)|but was|Exception|error:|cannot find symbol|Caused by");
    private static final Pattern BOILERPLATE = Pattern.compile(
            "^\\[ERROR]\\s*$|See .*(reports|dump files)|To see the full stack|Re-run Maven|For more information|\\[Help 1]"
                    + "|Failed to execute goal|Resolved Exception|^\\s*Type = ");
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private final String id;
    private final String goal;
    private final BuildRunner runner;

    public MavenGate(String id, String goal, BuildRunner runner) {
        this.id = id;
        this.goal = goal;
        this.runner = runner;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.staging() == null) {
            return GateResult.fail(id, id + " is an exit gate and needs a staged proposal");
        }
        String logName = context.node().id() + "-" + id + "-" + INVOCATIONS.incrementAndGet() + ".log";
        BuildRunner.BuildResult result = runner.run(context.staging(), List.of("-q", "-B", goal),
                context.logDir().resolve(logName));
        if (result.timedOut()) {
            return new GateResult.Fail(id, "mvn " + goal + " timed out after " + runner.timeoutSeconds() + " s",
                    "timeout");
        }
        if (result.succeeded()) {
            return GateResult.pass();
        }
        return GateResult.fail(id, "mvn " + goal + " failed (exit " + result.exitCode() + killedHint(result.exitCode())
                + "):\n" + condense(result.output()));
    }

    /** Exit 137 means SIGKILL: in the sandbox, almost always the container's memory limit. */
    static String killedHint(int exitCode) {
        return exitCode == KILLED ? ", the build was killed: raise build.sandbox.memory in policies.yaml if this repeats" : "";
    }

    /** Keeps the lines that explain the failure, then trims to {@link #MAX_FEEDBACK_CHARS}. */
    static String condense(String output) {
        List<String> salient = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (SALIENT.matcher(line).find() && !BOILERPLATE.matcher(line).find()) {
                salient.add(line.strip());
            }
        }
        String text = salient.isEmpty() ? output.strip() : String.join("\n", salient);
        return text.length() <= MAX_FEEDBACK_CHARS ? text : text.substring(0, MAX_FEEDBACK_CHARS);
    }
}
