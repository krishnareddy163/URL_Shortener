package com.example.agentic.core.engine;

import com.example.agentic.core.graph.WorkflowValidationException;
import com.example.agentic.core.report.IncidentWriter;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import com.example.agentic.core.workspace.PromotionConflictException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the run's stop decision. Starting a node, settling its output and failing it are each atomic with
 * respect to a concurrent stop, so after {@code SAFE_STOP} nothing starts, settles or fails, and the first
 * failure in a wave is the one that stops the run. Stopping appends {@code SAFE_STOP} (the fold marks every
 * non-DONE, non-FAILED node SKIPPED) and writes {@code incident.md}.
 */
public final class SafeStop {

    /** Work performed only while the run is not stopped. */
    @FunctionalInterface
    public interface Settlement<T> {
        T run() throws IOException, PromotionConflictException, WorkflowValidationException;
    }

    private final RunLog log;
    private final RunPaths paths;

    public SafeStop(RunLog log, RunPaths paths) {
        this.log = log;
        this.paths = paths;
    }

    public synchronized boolean isStopped() {
        return log.state().status() == RunState.RunStatus.SAFE_STOPPED;
    }

    /** Appends NODE_STARTED unless the run is stopped; returns whether the node may run. */
    public synchronized boolean startUnlessStopped(String nodeId, Payload.NodeStarted started, String inputHash) {
        if (isStopped()) {
            return false;
        }
        log.append(nodeId, started, RunLog.ENGINE, inputHash, null);
        return true;
    }

    /** Runs {@code action} unless the run is stopped; empty if it was stopped. */
    public synchronized <T> Optional<T> settleUnlessStopped(Settlement<T> action)
            throws IOException, PromotionConflictException, WorkflowValidationException {
        return isStopped() ? Optional.empty() : Optional.of(action.run());
    }

    /** Records NODE_FAILED and stops the run, unless another branch already stopped it; returns whether it did. */
    public synchronized boolean failNode(String nodeId, Payload.NodeFailed failure, String reason) {
        if (isStopped()) {
            return false;
        }
        log.append(nodeId, failure);
        stop(nodeId, reason);
        return true;
    }

    /** Stops the run (idempotent) and writes the incident report. */
    public synchronized void stop(String nodeId, String reason) {
        RunState state = log.state();
        if (state.status() == RunState.RunStatus.SAFE_STOPPED) {
            return;
        }
        log.append(nodeId, new Payload.SafeStopped(reason, nodeId, state.nodes().entrySet().stream()
                .filter(entry -> entry.getValue().status() == NodeStatus.DONE).map(Map.Entry::getKey).toList()));
        try {
            IncidentWriter.write(paths.incident(), log.runId(), log.events(), nodeId, reason);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
