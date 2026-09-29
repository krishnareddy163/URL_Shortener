package com.example.agentic.core.gate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code reproduces-defect}: proves that a bug-fix's regression test really reproduces the bug before any fix
 * exists. It runs {@code mvn -q -B test} on the staged tree and passes only when the build <em>fails</em>, the
 * failures are assertion failures in test classes the proposal added, and no other test class fails. A test that
 * passes on the unfixed code, does not compile, or breaks unrelated tests is not a reproduction.
 */
public final class ReproducesDefectGate implements Gate {
    public static final String GATE_ID = "reproduces-defect";
    private static final String TEST_ROOT = "src/test/java/";
    private static final Pattern FAILING_CLASS = Pattern.compile("<<< (?:FAILURE|ERROR)! -- in ([\\w.$]+)");
    private static final Pattern COMPILE_ERROR = Pattern.compile("COMPILATION ERROR|cannot find symbol|error: ");
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private final BuildRunner runner;

    public ReproducesDefectGate(BuildRunner runner) {
        this.runner = runner;
    }

    @Override
    public String id() {
        return GATE_ID;
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.staging() == null || context.proposal() == null) {
            return GateResult.fail(GATE_ID, GATE_ID + " is an exit gate and needs a staged proposal");
        }
        Set<String> added = addedTestClasses(context.proposal().files().keySet());
        if (added.isEmpty()) {
            return GateResult.fail(GATE_ID, "the proposal adds no test class under " + TEST_ROOT);
        }
        String logName = context.node().id() + "-" + GATE_ID + "-" + INVOCATIONS.incrementAndGet() + ".log";
        BuildRunner.BuildResult result = runner.run(context.staging(), List.of("-q", "-B", "test"),
                context.logDir().resolve(logName));
        if (result.timedOut()) {
            return new GateResult.Fail(GATE_ID, "mvn test timed out after " + runner.timeoutSeconds() + " s", "timeout");
        }
        return judge(result, added);
    }

    static GateResult judge(BuildRunner.BuildResult result, Set<String> added) {
        if (result.succeeded()) {
            return GateResult.fail(GATE_ID, "the new tests " + added + " pass on the unfixed code, so they do not reproduce "
                    + "the defect");
        }
        String output = result.output();
        if (COMPILE_ERROR.matcher(output).find()) {
            return GateResult.fail(GATE_ID, "the build fails to compile; a reproduction must compile and fail on an "
                    + "assertion:\n" + MavenGate.condense(output));
        }
        Set<String> failing = new TreeSet<>();
        Matcher matcher = FAILING_CLASS.matcher(output);
        while (matcher.find()) {
            failing.add(matcher.group(1));
        }
        if (failing.isEmpty()) {
            return GateResult.fail(GATE_ID, "mvn test failed without a failing test (exit " + result.exitCode() + "):\n"
                    + MavenGate.condense(output));
        }
        Set<String> unrelated = new TreeSet<>(failing);
        unrelated.removeAll(added);
        if (!unrelated.isEmpty()) {
            return GateResult.fail(GATE_ID, "tests outside the reproduction fail too " + unrelated + "; a reproduction "
                    + "must not break existing tests:\n" + MavenGate.condense(output));
        }
        return GateResult.pass();
    }

    /** Fully qualified names of test classes added by the proposal ({@code src/test/java/a/b/CTest.java -> a.b.CTest}). */
    static Set<String> addedTestClasses(Set<String> files) {
        Set<String> classes = new TreeSet<>();
        for (String file : files) {
            if (file.startsWith(TEST_ROOT) && file.endsWith("Test.java")) {
                classes.add(file.substring(TEST_ROOT.length(), file.length() - ".java".length()).replace('/', '.'));
            }
        }
        return classes;
    }
}
