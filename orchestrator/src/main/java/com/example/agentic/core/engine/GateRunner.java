package com.example.agentic.core.engine;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.workspace.Diff;
import com.example.agentic.core.workspace.Staging;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Evaluates a node's entry and exit gates in order, recording a GATE_PASSED/GATE_FAILED event for each. */
public final class GateRunner {
    static final String ENTRY = "entry";
    static final String APPLY = "apply";
    static final String EXIT = "exit";

    private final GateRegistry gates;
    private final RunLog log;
    private final Path workspaceRoot;
    private final Path logDir;

    public GateRunner(GateRegistry gates, RunLog log, Path workspaceRoot, Path logDir) {
        this.gates = gates;
        this.log = log;
        this.workspaceRoot = workspaceRoot;
        this.logDir = logDir;
    }

    /** First failing entry gate, if any. Entry gates see no proposal. */
    public Optional<GateResult.Fail> runEntryGates(Node node, Map<String, Artifact> upstream) {
        GateContext context = new GateContext(node, node.agent(), null, null, Diff.empty(), upstream, workspaceRoot, logDir);
        return runAll(node, node.entryGates(), context, ENTRY);
    }

    /** First failing exit gate, if any; evaluation stops at the first failure. */
    public Optional<GateResult.Fail> runExitGates(Node node, String agentId, Proposal proposal, Staging staging, Diff diff,
                                                  Map<String, Artifact> upstream) {
        GateContext context = new GateContext(node, agentId, proposal, staging.path(), diff, upstream, workspaceRoot, logDir);
        return runAll(node, node.exitGates(), context, EXIT);
    }

    /** Records a failure detected by the engine itself (path guard, graph patch) as a gate result. */
    public void recordFailure(String nodeId, GateResult.Fail failure, String phase) {
        log.append(nodeId, new Payload.GateFailed(failure.gateId(), phase, failure.reason(), failure.signature()));
    }

    private Optional<GateResult.Fail> runAll(Node node, List<String> gateIds, GateContext context, String phase) {
        for (String gateId : gateIds) {
            Optional<Gate> gate = gates.get(gateId);
            GateResult result = gate.isPresent() ? gate.get().evaluate(context)
                    : GateResult.fail(gateId, "gate is not registered: " + gateId);
            if (result instanceof GateResult.Fail fail) {
                recordFailure(node.id(), fail, phase);
                return Optional.of(fail);
            }
            log.append(node.id(), new Payload.GatePassed(gateId, phase, ((GateResult.Pass) result).detail()));
        }
        return Optional.empty();
    }
}
