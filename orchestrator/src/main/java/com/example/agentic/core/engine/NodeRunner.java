package com.example.agentic.core.engine;

import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.gate.LogSafe;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.workspace.PromotionConflictException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes one READY node (spec 6.3): entry gates, then up to {@code maxRetries + 1} attempts by the node's
 * agent (stopping early when the circuit breaker opens), then at most one round by the fallback agent. Each
 * attempt is run by {@link AttemptExecutor}; an accepted attempt is settled by {@link Settlement}. Exhausting
 * every round fails the node and safe-stops the run.
 */
public final class NodeRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRunner.class);

    /** Result of executing a node in the current wave. */
    public enum Result { DONE, WAITING, FAILED, STOPPED, PENDING }

    private final RunLog log;
    private final ArtifactStore artifacts;
    private final GateRunner gates;
    private final AttemptExecutor attempts;
    private final Settlement settlement;
    private final SafeStop safeStop;
    private final BudgetGuard budgets;
    private final ReplanService replan;

    NodeRunner(RunLog log, ArtifactStore artifacts, GateRunner gates, AttemptExecutor attempts, Settlement settlement,
               SafeStop safeStop, BudgetGuard budgets, ReplanService replan) {
        this.log = log;
        this.artifacts = artifacts;
        this.gates = gates;
        this.attempts = attempts;
        this.settlement = settlement;
        this.safeStop = safeStop;
        this.budgets = budgets;
        this.replan = replan;
    }

    public Result run(Node node) {
        RunState state = log.state();
        NodeInputs inputs = inputs(node, state);
        if (!safeStop.startUnlessStopped(node.id(), new Payload.NodeStarted(node.agent(), inputs.variant(),
                List.copyOf(inputs.upstream().keySet())), inputs.inputHash())) {
            return Result.STOPPED;
        }
        Optional<GateResult.Fail> entryFailure = gates.runEntryGates(node, inputs.upstream());
        if (entryFailure.isPresent()) {
            GateResult.Fail fail = entryFailure.get();
            return fail(node.id(), node.agent(), fail, "blocking entry gate failed: " + fail.reason(), false);
        }
        Round primary = runRound(inputs, node.agent(), false, state.node(node.id()).feedback());
        if (primary.result() != null) {
            return primary.result();
        }
        if (node.fallbackAgent() == null) {
            return failRound(node, node.agent(), primary);
        }
        log.append(node.id(), new Payload.Fallback(node.agent(), node.fallbackAgent(),
                primary.breakerTripped() ? "circuit breaker: identical failure signature twice" : "retries exhausted"));
        Round fallback = runRound(inputs, node.fallbackAgent(), true, primary.lastFailure().reason());
        return fallback.result() != null ? fallback.result() : failRound(node, node.fallbackAgent(), fallback);
    }

    private Round runRound(NodeInputs inputs, String agentId, boolean fallbackRound, String initialFeedback) {
        Node node = inputs.node();
        CircuitBreaker breaker = new CircuitBreaker();
        String feedback = initialFeedback;
        GateResult.Fail last = null;
        for (int tries = 1; tries <= node.maxRetries() + 1; tries++) {
            RunState current = log.state();
            if (current.status() == RunState.RunStatus.SAFE_STOPPED) {
                return Round.finished(Result.STOPPED);
            }
            Optional<String> exhausted = budgets.exceeded(current);
            if (exhausted.isPresent()) {
                return Round.finished(fail(node.id(), agentId,
                        (GateResult.Fail) GateResult.fail("budget", exhausted.get()), exhausted.get(), false));
            }
            int attempt = current.attempts(node.id(), agentId, inputs.inputHash()) + 1;
            Round outcome = attemptOnce(inputs, new AgentCall(agentId, attempt, fallbackRound, feedback));
            if (outcome.result() != null) {
                return outcome;
            }
            last = outcome.lastFailure();
            feedback = last.reason();
            if (breaker.recordFailure(last.signature())) {
                return new Round(null, last, true);
            }
        }
        return new Round(null, last, false);
    }

    /** One attempt plus settlement; engine faults (I/O, promotion conflicts) fail the node without retry. */
    private Round attemptOnce(NodeInputs inputs, AgentCall call) {
        AttemptExecutor.Outcome outcome;
        try {
            outcome = attempts.attempt(inputs, call);
        } catch (IOException exception) {
            return Round.finished(engineFault(inputs.node().id(), call, "engine-io", "engine I/O failure", exception));
        }
        return switch (outcome) {
            case AttemptExecutor.Rejected(GateResult.Fail failure) -> new Round(null, failure, false);
            case AttemptExecutor.Accepted accepted -> Round.finished(settle(inputs, call, accepted));
        };
    }

    private Result settle(NodeInputs inputs, AgentCall call, AttemptExecutor.Accepted accepted) {
        String nodeId = inputs.node().id();
        try {
            Optional<Result> settled = safeStop.settleUnlessStopped(
                    () -> settlement.settle(inputs, accepted.proposal(), accepted.staging(), accepted.diff()));
            if (settled.isEmpty()) {
                AttemptExecutor.discardQuietly(accepted.staging());
                return Result.STOPPED;
            }
            return settled.get();
        } catch (IOException exception) {
            AttemptExecutor.discardQuietly(accepted.staging());
            return engineFault(nodeId, call, "engine-io", "engine I/O failure", exception);
        } catch (PromotionConflictException | WorkflowValidationException exception) {
            AttemptExecutor.discardQuietly(accepted.staging());
            return engineFault(nodeId, call, "promotion", "hard failure while promoting", exception);
        }
    }

    private Result engineFault(String nodeId, AgentCall call, String gateId, String description, Exception exception) {
        return fail(nodeId, call.agentId(), (GateResult.Fail) GateResult.fail(gateId, exception.getMessage()),
                description + ": " + exception.getMessage(), false);
    }

    private Result failRound(Node node, String agentId, Round round) {
        String upstreamId = node.rejectUpstream();
        if (upstreamId != null) {
            long cycles = log.events().stream()
                    .filter(e -> e.type() == EventType.UPSTREAM_REJECTED && upstreamId.equals(e.nodeId()))
                    .filter(e -> node.id().equals(e.payload(Payload.UpstreamRejected.class).downstream()))
                    .count();
            if (cycles < node.rejectUpstreamMaxCycles()) {
                try {
                    replan.rejectUpstream(node.id(), upstreamId, round.lastFailure().reason());
                    return Result.PENDING;
                } catch (IOException | PromotionConflictException exception) {
                    LOGGER.warn("upstream rejection revert failed for '{}'; falling through to safe-stop: {}",
                            LogSafe.clean(upstreamId), LogSafe.clean(exception.getMessage()));
                }
            }
        }
        String cause = round.breakerTripped() ? "circuit breaker tripped" : "retries exhausted";
        return fail(node.id(), agentId, round.lastFailure(),
                cause + " for '" + node.id() + "': " + firstLine(round.lastFailure().reason()), round.breakerTripped());
    }

    private Result fail(String nodeId, String agentId, GateResult.Fail failure, String reason, boolean breakerTripped) {
        boolean failed = safeStop.failNode(nodeId, new Payload.NodeFailed(agentId, failure.gateId(), failure.signature(),
                failure.reason(), breakerTripped), reason);
        return failed ? Result.FAILED : Result.STOPPED;
    }

    private NodeInputs inputs(Node node, RunState state) {
        String variant = Clarifications.variantKey(node.fixtureVariantFrom(), state.answers());
        Map<String, Artifact> upstream = new LinkedHashMap<>();
        for (String id : state.graph().upstreamOf(node.id())) {
            String hash = state.node(id).currentHash();
            if (hash != null) {
                artifacts.get(hash).ifPresent(artifact -> upstream.put(id, artifact));
            }
        }
        String inputHash = Hashing.inputHash(upstream.values().stream().map(Artifact::hash).toList(), variant);
        return new NodeInputs(node, variant, inputHash, upstream);
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse(text);
    }

    /**
     * Outcome of one attempt or one agent's round: a terminal node result, or the (last) failure to act on.
     *
     * @param result         terminal node result, or {@code null} if the node should try again
     * @param lastFailure    the failure, when {@code result} is {@code null}
     * @param breakerTripped whether identical failures opened the circuit breaker
     */
    private record Round(Result result, GateResult.Fail lastFailure, boolean breakerTripped) {
        static Round finished(Result result) {
            return new Round(result, null, false);
        }
    }
}
