package com.example.agentic.core.metrics;

import com.example.agentic.core.state.ModelUsage;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Reliability and governance metrics of one run, derived only from its event log.
 *
 * @param totalNodes          nodes in the final graph
 * @param firstPassNodes      nodes DONE with no failed gate or discarded attempt
 * @param retriesPerNode      discarded attempts per node (nodes with none are omitted)
 * @param retries             total discarded attempts
 * @param rollbacks           staging discards (every discarded attempt is a rollback)
 * @param fallbacks           fallback-agent rounds
 * @param recoveredNodes      nodes that failed at least once and later reached DONE
 * @param mttr                mean time from a node's first failure to its next DONE
 * @param grossLatency        RUN_STARTED to the last RUN_COMPLETED
 * @param humanWait           union of approval, clarification and post-completion idle intervals
 * @param netLatency          gross latency minus human wait
 * @param approvalsRequested  APPROVAL_REQUESTED count
 * @param approvalsGranted    APPROVED count
 * @param approvalsRejected   REJECTED count
 * @param clarifications      CLARIFICATION_REQUESTED count
 * @param invalidations       INVALIDATED count
 * @param replans             REPLAN count
 * @param gateFailuresByGate  GATE_FAILED count per gate id
 * @param modelUsage          model calls, tokens and model time summed over every AGENT_CALLED (zero in MOCK runs)
 */
public record RunMetrics(
        int totalNodes,
        int firstPassNodes,
        Map<String, Integer> retriesPerNode,
        int retries,
        int rollbacks,
        int fallbacks,
        int recoveredNodes,
        Optional<Duration> mttr,
        Optional<Duration> grossLatency,
        Duration humanWait,
        Optional<Duration> netLatency,
        int approvalsRequested,
        int approvalsGranted,
        int approvalsRejected,
        int clarifications,
        int invalidations,
        int replans,
        Map<String, Integer> gateFailuresByGate,
        ModelUsage modelUsage) {

    public RunMetrics {
        retriesPerNode = Map.copyOf(retriesPerNode);
        gateFailuresByGate = Map.copyOf(gateFailuresByGate);
    }

    public double successRate() {
        return totalNodes == 0 ? 0 : (double) firstPassNodes / totalNodes;
    }

    /** Markdown table of every metric. */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder("| Metric | Value |\n|---|---|\n");
        row(out, "Success rate (first-pass DONE / nodes)",
                String.format(Locale.ROOT, "%.1f%% (%d/%d)", successRate() * 100, firstPassNodes, totalNodes));
        row(out, "Retries (discarded attempts)", retries + (retriesPerNode.isEmpty() ? "" : " " + new TreeMap<>(retriesPerNode)));
        row(out, "Rollbacks (staging discarded)", String.valueOf(rollbacks));
        row(out, "Fallbacks", String.valueOf(fallbacks));
        row(out, "MTTR", mttr.map(RunMetrics::format).orElse("n/a (no recovered failures)") + " over " + recoveredNodes + " node(s)");
        row(out, "End-to-end latency (gross)", grossLatency.map(RunMetrics::format).orElse("n/a (not completed)"));
        row(out, "Human wait excluded", format(humanWait));
        row(out, "End-to-end latency (net)", netLatency.map(RunMetrics::format).orElse("n/a (not completed)"));
        row(out, "Approvals requested / granted / rejected", approvalsRequested + " / " + approvalsGranted + " / " + approvalsRejected);
        row(out, "Clarifications requested", String.valueOf(clarifications));
        row(out, "Invalidations / replans", invalidations + " / " + replans);
        row(out, "Gate failures by gate", gateFailuresByGate.isEmpty() ? "none" : new TreeMap<>(gateFailuresByGate).toString());
        if (modelUsage.calls() > 0) {
            row(out, "Model calls / input tokens / output tokens / model time", String.format(Locale.ROOT, "%d / %d / %d / %s",
                    modelUsage.calls(), modelUsage.inputTokens(), modelUsage.outputTokens(),
                    format(Duration.ofMillis(modelUsage.millis()))));
        }
        return out.toString();
    }

    static String format(Duration duration) {
        return String.format(Locale.ROOT, "%.3f s", duration.toMillis() / 1000.0);
    }

    private static void row(StringBuilder out, String name, String value) {
        out.append("| ").append(name).append(" | ").append(value.replace("|", "\\|")).append(" |\n");
    }
}
