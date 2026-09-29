package com.example.agentic.core.policy;

import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.workspace.Diff;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Compliance gate: an added logging call ({@code log.} / {@code logger.}) must not reference the raw client
 * address (tokens from policies.yaml, e.g. {@code getRemoteAddr}, {@code remoteAddr}).
 */
public final class NoRawIpLoggingGate implements Gate {
    private static final Pattern LOG_CALL = Pattern.compile("(?i)\\b(log|logger)\\s*\\.");
    private final List<String> tokens;

    public NoRawIpLoggingGate(PolicyConfig policy) {
        tokens = policy.rawIpTokens();
    }

    @Override
    public String id() {
        return "no-raw-ip-logging";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        for (Diff.FileChange change : context.diff().changes()) {
            for (String line : change.addedLines()) {
                if (!LOG_CALL.matcher(line).find()) {
                    continue;
                }
                for (String token : tokens) {
                    if (line.contains(token)) {
                        return GateResult.fail(id(), "raw client address logged in " + change.path() + ": " + line.strip());
                    }
                }
            }
        }
        return GateResult.pass();
    }
}
