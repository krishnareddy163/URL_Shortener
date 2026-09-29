package com.example.agentic.core.gate;

import com.example.agentic.core.state.Artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Functional coverage exit gate for the QA report. The proposal's {@code functionalCoverage} maps acceptance
 * criteria to tests ({@code {"criterion": ..., "tests": ["ClassName#method", ...]}}). Every acceptance criterion of
 * the upstream requirements must be mapped to at least one test, and every referenced test must exist as a method in
 * the staged {@code src/test/java} tree, so the matrix cannot cite tests that are not there.
 */
public final class FunctionalCoverageGate implements Gate {
    private static final Pattern TEST_REFERENCE = Pattern.compile("([A-Za-z_$][\\w$]*)#([A-Za-z_$][\\w$]*)");

    @Override
    public String id() {
        return "functional-coverage";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.staging() == null) {
            return GateResult.fail(id(), "functional-coverage is an exit gate and needs a staged proposal");
        }
        Set<String> criteria = acceptanceCriteria(context.upstream().values());
        if (criteria.isEmpty()) {
            return GateResult.fail(id(), "no upstream acceptance criteria to map to tests");
        }
        if (!(context.proposal().data().get("functionalCoverage") instanceof List<?> matrix)) {
            return GateResult.fail(id(), "functionalCoverage must map each acceptance criterion to tests");
        }
        Set<String> mapped = new LinkedHashSet<>();
        Set<String> tests = new LinkedHashSet<>();
        for (Object row : matrix) {
            if (!(row instanceof Map<?, ?> entry) || !(entry.get("criterion") instanceof String criterion)
                    || !(entry.get("tests") instanceof List<?> references) || references.isEmpty()) {
                return GateResult.fail(id(), "each functionalCoverage entry needs criterion and a non-empty tests list");
            }
            for (Object reference : references) {
                if (!(reference instanceof String test) || !TEST_REFERENCE.matcher(test).matches()) {
                    return GateResult.fail(id(), "test reference must read ClassName#method: " + reference);
                }
                tests.add(test);
            }
            mapped.add(criterion);
        }
        List<String> unmapped = criteria.stream().filter(criterion -> !mapped.contains(criterion)).toList();
        if (!unmapped.isEmpty()) {
            return GateResult.fail(id(), unmapped.size() + " acceptance criterion/criteria not mapped to any test: "
                    + String.join(" | ", unmapped));
        }
        Optional<String> missing = firstMissingTest(context.staging().resolve("src/test/java"), tests);
        if (missing.isPresent()) {
            return GateResult.fail(id(), "referenced test does not exist: " + missing.get());
        }
        return GateResult.pass(criteria.size() + " of " + criteria.size() + " acceptance criteria mapped to "
                + tests.size() + " existing tests");
    }

    private static Set<String> acceptanceCriteria(Iterable<Artifact> upstream) {
        Set<String> criteria = new LinkedHashSet<>();
        for (Artifact artifact : upstream) {
            if (artifact.data().get("acceptanceCriteria") instanceof List<?> list) {
                list.forEach(item -> criteria.add(String.valueOf(item)));
            }
        }
        return criteria;
    }

    private static Optional<String> firstMissingTest(Path testRoot, Set<String> tests) {
        List<Path> sources = new ArrayList<>();
        if (Files.isDirectory(testRoot)) {
            try (Stream<Path> walk = Files.walk(testRoot)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            } catch (IOException exception) {
                return Optional.of("cannot read " + testRoot + ": " + exception.getMessage());
            }
        }
        for (String test : tests) {
            String[] parts = test.split("#");
            Pattern method = Pattern.compile("\\bvoid\\s+" + Pattern.quote(parts[1]) + "\\s*\\(");
            boolean found = sources.stream()
                    .filter(source -> (parts[0] + ".java").equals(String.valueOf(source.getFileName())))
                    .anyMatch(source -> method.matcher(readQuietly(source)).find());
            if (!found) {
                return Optional.of(test);
            }
        }
        return Optional.empty();
    }

    private static String readQuietly(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException _) {
            return "";
        }
    }
}
