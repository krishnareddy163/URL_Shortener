package com.example.agentic.core.policy;

import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.workspace.Diff;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Security gate: added lines in {@code src/main/**} must not use process execution, Java deserialization,
 * reflective class loading of non-literals, or script engines. Patterns come from policies.yaml.
 */
public final class ForbiddenApiGate implements Gate {
    private final List<Pattern> patterns;

    public ForbiddenApiGate(PolicyConfig policy) {
        patterns = policy.forbiddenApiPatterns().stream().map(Pattern::compile).toList();
    }

    @Override
    public String id() {
        return "forbidden-api";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        for (Diff.FileChange change : context.diff().changes()) {
            if (!change.path().startsWith("src/main/")) {
                continue;
            }
            for (String line : change.addedLines()) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(line).find()) {
                        return GateResult.fail(id(), "forbidden API /" + pattern.pattern() + "/ in " + change.path()
                                + ": " + line.strip());
                    }
                }
            }
        }
        return GateResult.pass();
    }
}
