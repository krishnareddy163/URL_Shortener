package com.example.agentic.core.policy;

import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.workspace.Diff;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Security gate: regex scan of added lines for credentials (AWS access key ids, private key blocks,
 * literal {@code password=} and {@code api_key=} assignments). Patterns come from policies.yaml.
 * The matched text is never echoed, only the file and pattern index.
 */
public final class SecretScanGate implements Gate {
    private final List<Pattern> patterns;

    public SecretScanGate(PolicyConfig policy) {
        patterns = policy.secretPatterns().stream().map(Pattern::compile).toList();
    }

    @Override
    public String id() {
        return "secret-scan";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        for (Diff.FileChange change : context.diff().changes()) {
            for (String line : change.addedLines()) {
                for (int index = 0; index < patterns.size(); index++) {
                    if (patterns.get(index).matcher(line).find()) {
                        return GateResult.fail(id(), "possible secret (pattern #" + index + ") added in " + change.path());
                    }
                }
            }
        }
        return GateResult.pass();
    }
}
