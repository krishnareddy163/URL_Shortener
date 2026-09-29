package com.example.agentic.core.engine;

import com.example.agentic.core.gate.LogSafe;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunLog;
import com.example.agentic.core.state.RunState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wave scheduler with a join barrier: each iteration runs every READY node concurrently on virtual threads,
 * waits for all of them, re-reads state from the event log and re-checks budgets. A node with several
 * dependencies is READY only when all of them are DONE. When nothing is READY the run completes, pauses for
 * humans (other branches having already run as far as they can), or safe-stops.
 */
public final class Scheduler {
    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private final RunLog runLog;
    private final NodeRunner runner;
    private final SafeStop safeStop;
    private final BudgetGuard budgets;

    public Scheduler(RunLog runLog, NodeRunner runner, SafeStop safeStop, BudgetGuard budgets) {
        this.runLog = runLog;
        this.runner = runner;
        this.safeStop = safeStop;
        this.budgets = budgets;
    }

    public RunOutcome run() {
        while (true) {
            RunState state = runLog.state();
            if (state.status() == RunState.RunStatus.SAFE_STOPPED) {
                return RunOutcome.SAFE_STOPPED;
            }
            Optional<String> exhausted = budgets.exceeded(state);
            if (exhausted.isPresent()) {
                safeStop.stop(null, exhausted.get());
                return RunOutcome.SAFE_STOPPED;
            }
            List<Node> ready = state.readyNodes();
            if (ready.isEmpty()) {
                return settle(state);
            }
            runWave(ready);
        }
    }

    private RunOutcome settle(RunState state) {
        if (state.allDone()) {
            runLog.append(null, new Payload.RunCompleted(state.nodes().size()));
            return RunOutcome.COMPLETED;
        }
        if (state.anyAwaitingHuman()) {
            Map<String, String> waiting = new LinkedHashMap<>();
            state.nodes().forEach((id, node) -> {
                if (node.status() == NodeStatus.AWAITING_APPROVAL || node.status() == NodeStatus.AWAITING_CLARIFICATION) {
                    waiting.put(id, node.status().name());
                }
            });
            runLog.append(null, new Payload.RunPaused(waiting));
            return RunOutcome.PAUSED;
        }
        safeStop.stop(null, "no runnable nodes remain: " + state.nodes());
        return RunOutcome.SAFE_STOPPED;
    }

    private void runWave(List<Node> ready) {
        if (log.isDebugEnabled()) {
            log.debug("wave starting: {}", LogSafe.clean(ready.stream().map(Node::id).toList()));
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<NodeRunner.Result>> wave = new ArrayList<>();
            for (Node node : ready) {
                wave.add(CompletableFuture.supplyAsync(() -> runner.run(node), executor));
            }
            CompletableFuture.allOf(wave.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("node execution failed unexpectedly", exception.getCause());
        }
    }
}
