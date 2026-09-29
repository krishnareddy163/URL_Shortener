package com.example.agentic.core.policy;

import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.workspace.PathGuard;

import java.util.List;
import java.util.Optional;

/**
 * Change-control gate. As an entry gate it requires the agent to have a declared path scope; as an exit
 * gate it re-checks every proposed path against that scope (PathGuard already enforces this while
 * applying the proposal; this makes the check visible in the audit trail).
 */
public final class PathAllowlistGate implements Gate {
    private final PolicyConfig policy;

    public PathAllowlistGate(PolicyConfig policy) {
        this.policy = policy;
    }

    @Override
    public String id() {
        return "path-allowlist";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        Optional<List<String>> scope = policy.scopeFor(context.agentId());
        if (scope.isEmpty()) {
            return GateResult.fail(id(), "agent '" + context.agentId() + "' has no path scope in policies.yaml");
        }
        if (context.isEntry()) {
            return GateResult.pass();
        }
        for (String path : context.proposal().files().keySet()) {
            if (!PathGuard.matchesAny(path, scope.get())) {
                return GateResult.fail(id(), "path outside scope " + scope.get() + ": " + path);
            }
        }
        return GateResult.pass();
    }
}
