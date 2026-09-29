package com.example.agentic.core.metrics;

import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.ModelUsage;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes {@link RunMetrics} from events alone.
 *
 * <p>Latency: gross is RUN_STARTED to the last RUN_COMPLETED. Net subtracts the union of human-wait
 * intervals: APPROVAL_REQUESTED to the node's next APPROVED/REJECTED/INVALIDATED, CLARIFICATION_REQUESTED
 * to the question's ANSWERED, and RUN_COMPLETED to a later RESUMED (idle until a human reopened the run).
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    public static RunMetrics calculate(List<Event> events) {
        RunState finalState = RunState.fold(events);
        Tally tally = tally(events);
        int totalNodes = finalState.graph() == null ? 0 : finalState.graph().nodes().size();
        int firstPass = (int) finalState.nodes().entrySet().stream()
                .filter(entry -> entry.getValue().status() == NodeStatus.DONE)
                .filter(entry -> !tally.failingNodes().contains(entry.getKey()))
                .count();
        int retries = tally.discarded().values().stream().mapToInt(Integer::intValue).sum();
        List<Duration> repairs = repairTimes(events);
        Optional<Duration> mttr = repairs.isEmpty() ? Optional.empty()
                : Optional.of(repairs.stream().reduce(Duration.ZERO, Duration::plus).dividedBy(repairs.size()));
        Latency latency = latency(events);
        return new RunMetrics(totalNodes, firstPass, tally.discarded(), retries, retries, tally.count(EventType.FALLBACK),
                repairs.size(), mttr, latency.gross(), latency.humanWait(), latency.net(),
                tally.count(EventType.APPROVAL_REQUESTED), tally.count(EventType.APPROVED), tally.count(EventType.REJECTED),
                tally.count(EventType.CLARIFICATION_REQUESTED), tally.count(EventType.INVALIDATED),
                tally.count(EventType.REPLAN), tally.gateFailures(), tally.modelUsage());
    }

    private record Tally(Map<String, Integer> discarded, Map<String, Integer> gateFailures, Set<String> failingNodes,
                         Map<EventType, Integer> counts, ModelUsage modelUsage) {
        int count(EventType type) {
            return counts.getOrDefault(type, 0);
        }
    }

    private static Tally tally(List<Event> events) {
        Map<String, Integer> discarded = new TreeMap<>();
        Map<String, Integer> gateFailures = new TreeMap<>();
        Set<String> failingNodes = new HashSet<>();
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        ModelUsage modelUsage = ModelUsage.NONE;
        for (Event event : events) {
            counts.merge(event.type(), 1, Integer::sum);
            if (event.type() == EventType.AGENT_CALLED) {
                ModelUsage usage = event.payload(Payload.AgentCalled.class).usage();
                modelUsage = usage == null ? modelUsage : modelUsage.plus(usage);
            } else if (event.type() == EventType.ATTEMPT_DISCARDED) {
                discarded.merge(event.nodeId(), 1, Integer::sum);
                failingNodes.add(event.nodeId());
            } else if (event.type() == EventType.GATE_FAILED) {
                gateFailures.merge(event.payload(Payload.GateFailed.class).gate(), 1, Integer::sum);
                failingNodes.add(event.nodeId());
            }
        }
        return new Tally(discarded, gateFailures, failingNodes, counts, modelUsage);
    }

    private record Latency(Optional<Duration> gross, Duration humanWait, Optional<Duration> net) {
    }

    private static Latency latency(List<Event> events) {
        Optional<Instant> start = events.stream().filter(event -> event.type() == EventType.RUN_STARTED)
                .map(Event::ts).findFirst();
        Optional<Instant> end = events.stream().filter(event -> event.type() == EventType.RUN_COMPLETED)
                .map(Event::ts).reduce((first, last) -> last);
        Optional<Duration> gross = start.flatMap(from -> end.map(to -> Duration.between(from, to)));
        Instant windowEnd = end.orElseGet(() -> events.isEmpty() ? Instant.EPOCH : events.getLast().ts());
        Duration wait = union(waitIntervals(events, windowEnd), start.orElse(windowEnd), windowEnd);
        Optional<Duration> net = gross.map(value -> value.minus(wait).isNegative() ? Duration.ZERO : value.minus(wait));
        return new Latency(gross, wait, net);
    }

    /** For each node that failed and later reached DONE: first failure to the next NODE_DONE. */
    private static List<Duration> repairTimes(List<Event> events) {
        Map<String, Instant> firstFailure = new HashMap<>();
        Map<String, Duration> repaired = new TreeMap<>();
        for (Event event : events) {
            String node = event.nodeId();
            if (node == null || repaired.containsKey(node)) {
                continue;
            }
            if (event.type() == EventType.GATE_FAILED || event.type() == EventType.NODE_FAILED) {
                firstFailure.putIfAbsent(node, event.ts());
            } else if (event.type() == EventType.NODE_DONE && firstFailure.containsKey(node)) {
                repaired.put(node, Duration.between(firstFailure.get(node), event.ts()));
            }
        }
        return new ArrayList<>(repaired.values());
    }

    private record Interval(Instant from, Instant to) {
    }

    private static List<Interval> waitIntervals(List<Event> events, Instant windowEnd) {
        List<Interval> intervals = new ArrayList<>();
        Map<String, Instant> approvalOpen = new HashMap<>();
        Map<String, Instant> questionOpen = new HashMap<>();
        Instant completedAt = null;
        for (Event event : events) {
            switch (event.type()) {
                case APPROVAL_REQUESTED -> approvalOpen.put(event.nodeId(), event.ts());
                case APPROVED, REJECTED, INVALIDATED -> close(approvalOpen.remove(event.nodeId()), event.ts(), intervals);
                case CLARIFICATION_REQUESTED -> questionOpen.put(event.payload(Payload.ClarificationRequested.class).questionId(), event.ts());
                case ANSWERED -> close(questionOpen.remove(event.payload(Payload.Answered.class).questionId()), event.ts(), intervals);
                case RUN_COMPLETED -> completedAt = event.ts();
                case RESUMED -> {
                    close(completedAt, event.ts(), intervals);
                    completedAt = null;
                }
                default -> {
                    // Other events do not open or close waits.
                }
            }
        }
        approvalOpen.values().forEach(from -> close(from, windowEnd, intervals));
        questionOpen.values().forEach(from -> close(from, windowEnd, intervals));
        return intervals;
    }

    private static void close(Instant from, Instant to, List<Interval> intervals) {
        if (from != null && to.isAfter(from)) {
            intervals.add(new Interval(from, to));
        }
    }

    /** Total length of the union of intervals, clipped to [windowStart, windowEnd]. */
    private static Duration union(List<Interval> intervals, Instant windowStart, Instant windowEnd) {
        List<Interval> sorted = intervals.stream().sorted(Comparator.comparing(Interval::from)).toList();
        Duration total = Duration.ZERO;
        Instant coveredUntil = windowStart;
        for (Interval interval : sorted) {
            Instant from = interval.from().isBefore(coveredUntil) ? coveredUntil : interval.from();
            Instant to = interval.to().isAfter(windowEnd) ? windowEnd : interval.to();
            if (to.isAfter(from)) {
                total = total.plus(Duration.between(from, to));
                coveredUntil = to;
            }
        }
        return total;
    }
}
