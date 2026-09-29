package com.example.agentic.core.engine;

import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.AgentResolver;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.agent.UsageMeter;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.gate.LogSafe;
import com.example.agentic.core.graph.GraphPatch;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.ModelUsage;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.workspace.Diff;
import com.example.agentic.core.workspace.PathGuard;
import com.example.agentic.core.workspace.PathViolationException;
import com.example.agentic.core.workspace.Staging;
import com.example.agentic.core.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One attempt of one agent: call the agent, apply its proposal to a fresh staging copy under PathGuard, run
 * the exit gates and validate any graph patch. A rejected attempt is rolled back (staging deleted,
 * ATTEMPT_DISCARDED recorded); an accepted one hands its staging to the caller for settlement.
 */
final class AttemptExecutor {
    private static final Logger log = LoggerFactory.getLogger(AttemptExecutor.class);
    private static final String GRAPH_PATCH = "graph-patch";
    private static final String PATH_ALLOWLIST = "path-allowlist";

    /** Result of an attempt. */
    sealed interface Outcome {
    }

    /**
     * Every gate passed; the caller owns {@code staging} from here on.
     *
     * @param proposal the agent's proposal
     * @param staging  staged tree with the proposal applied
     * @param diff     changes relative to the staging base
     */
    record Accepted(Proposal proposal, Staging staging, Diff diff) implements Outcome {
    }

    /**
     * The attempt failed and was rolled back.
     *
     * @param failure what failed, with its signature
     */
    record Rejected(GateResult.Fail failure) implements Outcome {
    }

    private final RunLog runLog;
    private final AgentResolver agents;
    private final Workspace workspace;
    private final PolicyConfig policy;
    private final GateRunner gates;
    private final ReplanService replan;
    private final PathGuard pathGuard = new PathGuard();

    AttemptExecutor(RunLog runLog, AgentResolver agents, Workspace workspace, PolicyConfig policy, GateRunner gates,
                    ReplanService replan) {
        this.runLog = runLog;
        this.agents = agents;
        this.workspace = workspace;
        this.policy = policy;
        this.gates = gates;
        this.replan = replan;
    }

    /**
     * Runs one attempt.
     *
     * @throws IOException if staging cannot be created (an engine fault, not an agent failure)
     */
    Outcome attempt(NodeInputs inputs, AgentCall call) throws IOException {
        String nodeId = inputs.node().id();
        RunState state = runLog.state();
        AgentContext context = new AgentContext(runLog.runId(), nodeId, call.agentId(), call.attempt(), call.fallbackRound(),
                inputs.variant(), state.graph().requirement(), inputs.upstream(), state.answers(), call.feedback(),
                workspace.view(), inputs.node().task(), new UsageMeter());
        String promptHash = promptHash(context);
        Optional<Agent> agent = agents.resolve(call.agentId());
        Map<String, String> agentMetadata = agent.map(Agent::metadata).orElse(Map.of());
        Proposal proposal;
        try {
            if (agent.isEmpty()) {
                throw new AgentException("no agent registered for id '" + call.agentId() + "'");
            }
            proposal = agent.get().propose(context);
        } catch (AgentException | RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            recordCall(inputs, call, new Payload.AgentCalled(call.agentId(), call.attempt(), call.fallbackRound(), promptHash,
                    null, message, agentMetadata, usage(context)));
            return reject(nodeId, call, null, (GateResult.Fail) GateResult.fail("agent", message));
        }
        String responseHash = Hashing.sha256(Hashing.canonicalJson(Map.of("files", proposal.files(),
                "data", proposal.data(), "rationale", String.valueOf(proposal.rationale()),
                "derivedFrom", proposal.derivedFrom())));
        recordCall(inputs, call, new Payload.AgentCalled(call.agentId(), call.attempt(), call.fallbackRound(), promptHash,
                responseHash, null, agentMetadata, usage(context)));
        return gate(inputs, call, proposal, workspace.stage(nodeId));
    }

    /** Deletes a staging directory that will not be settled, without recording a failure. */
    static void discardQuietly(Staging staging) {
        if (staging == null) {
            return;
        }
        try {
            staging.discard();
        } catch (IOException exception) {
            log.warn("could not delete staging directory (it is cleared on resume): {}", LogSafe.clean(exception.getMessage()));
        }
    }

    private Outcome gate(NodeInputs inputs, AgentCall call, Proposal proposal, Staging staging) throws IOException {
        try {
            return gateStaged(inputs, call, proposal, staging);
        } catch (IOException | RuntimeException exception) {
            discardQuietly(staging);
            throw exception;
        }
    }

    private Outcome gateStaged(NodeInputs inputs, AgentCall call, Proposal proposal, Staging staging) throws IOException {
        String nodeId = inputs.node().id();
        try {
            staging.apply(proposal.files(), pathGuard, policy.scopeFor(call.agentId()).orElse(List.of()));
        } catch (PathViolationException violation) {
            GateResult.Fail fail = (GateResult.Fail) GateResult.fail(PATH_ALLOWLIST, violation.getMessage());
            gates.recordFailure(nodeId, fail, GateRunner.APPLY);
            return reject(nodeId, call, staging, fail);
        }
        Diff diff = staging.diff();
        Optional<GateResult.Fail> gateFailure = gates.runExitGates(inputs.node(), call.agentId(), proposal, staging, diff,
                inputs.upstream());
        if (gateFailure.isPresent()) {
            return reject(nodeId, call, staging, gateFailure.get());
        }
        Optional<String> patchProblem = patchProblem(proposal);
        if (patchProblem.isPresent()) {
            GateResult.Fail fail = (GateResult.Fail) GateResult.fail(GRAPH_PATCH, patchProblem.get());
            gates.recordFailure(nodeId, fail, GateRunner.EXIT);
            return reject(nodeId, call, staging, fail);
        }
        return new Accepted(proposal, staging, diff);
    }

    private Optional<String> patchProblem(Proposal proposal) {
        ReplanService.PatchParse parse = ReplanService.patchIn(proposal.data());
        if (parse.error().isPresent()) {
            return parse.error();
        }
        Optional<GraphPatch> patch = parse.patch();
        if (patch.isEmpty()) {
            return Optional.empty();
        }
        List<String> errors = replan.validate(patch.get(), runLog.state());
        return errors.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", errors));
    }

    private Rejected reject(String nodeId, AgentCall call, Staging staging, GateResult.Fail failure) {
        discardQuietly(staging);
        runLog.append(nodeId, new Payload.AttemptDiscarded(call.agentId(), call.attempt(), failure.gateId(),
                failure.signature(), failure.reason()));
        return new Rejected(failure);
    }

    private void recordCall(NodeInputs inputs, AgentCall call, Payload.AgentCalled called) {
        runLog.append(inputs.node().id(), called, call.agentId(), inputs.inputHash(), null);
    }

    private static ModelUsage usage(AgentContext context) {
        ModelUsage usage = context.usage().total();
        return usage.calls() == 0 ? null : usage;
    }

    private static String promptHash(AgentContext context) {
        Map<String, Object> prompt = new TreeMap<>();
        prompt.put("node", context.nodeId());
        prompt.put("agent", context.agentId());
        prompt.put("attempt", context.attempt());
        prompt.put("fallbackRound", context.fallbackRound());
        prompt.put("variant", context.variantKey());
        prompt.put("requirement", context.requirement());
        Map<String, String> upstream = new TreeMap<>();
        context.upstream().forEach((id, artifact) -> upstream.put(id, artifact.hash()));
        prompt.put("upstream", upstream);
        prompt.put("answers", context.answers());
        prompt.put("feedback", context.feedback() == null ? "" : context.feedback());
        if (context.task() != null) {
            prompt.put("task", context.task());
        }
        return Hashing.sha256(Hashing.canonicalJson(prompt));
    }
}
