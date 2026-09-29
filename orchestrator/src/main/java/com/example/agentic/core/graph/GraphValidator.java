package com.example.agentic.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validation of a workflow graph: ids, references, retry limits, budgets and acyclicity.
 * Used at load time and again for every graph patch.
 */
public final class GraphValidator {
    private static final Pattern NODE_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Set<String> BUDGET_KEYS = Set.of("maxTotalAttempts", "maxWallClockSeconds", "maxAgentCalls");

    private final Set<String> agentIds;
    private final Set<String> gateIds;

    public GraphValidator(Set<String> agentIds, Set<String> gateIds) {
        this.agentIds = Set.copyOf(agentIds);
        this.gateIds = Set.copyOf(gateIds);
    }

    public void validateOrThrow(WorkflowGraph graph) throws WorkflowValidationException {
        List<String> errors = validate(graph);
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /** Returns all problems found; an empty list means the graph is valid. */
    public List<String> validate(WorkflowGraph graph) {
        List<String> errors = new ArrayList<>();
        validateHeader(graph, errors);
        Set<String> ids = validateIds(graph, errors);
        for (Node node : graph.nodes()) {
            validateNode(node, ids, errors);
        }
        findCycle(graph).ifPresent(cycle -> errors.add("dependency cycle: " + String.join(" -> ", cycle)));
        return errors;
    }

    private static void validateHeader(WorkflowGraph graph, List<String> errors) {
        if (graph.name() == null || graph.name().isBlank()) {
            errors.add("workflow name is required");
        }
        if (graph.requirement() == null || graph.requirement().isBlank()) {
            errors.add("workflow requirement is required");
        }
        if (!graph.workspace().equals("empty") && !graph.workspace().startsWith("copy:")) {
            errors.add("workspace must be 'empty' or 'copy:<path>', was '" + graph.workspace() + "'");
        }
        graph.budgets().forEach((key, value) -> {
            if (!BUDGET_KEYS.contains(key)) {
                errors.add("unknown budget '" + key + "'");
            } else if (value == null || value <= 0) {
                errors.add("budget '" + key + "' must be positive");
            }
        });
        if (graph.nodes().isEmpty()) {
            errors.add("workflow must declare at least one node");
        }
    }

    private static Set<String> validateIds(WorkflowGraph graph, List<String> errors) {
        Set<String> ids = new HashSet<>();
        for (Node node : graph.nodes()) {
            if (node.id() == null || !NODE_ID.matcher(node.id()).matches()) {
                errors.add("node id '" + node.id() + "' must match " + NODE_ID.pattern());
            } else if (!ids.add(node.id())) {
                errors.add("duplicate node id '" + node.id() + "'");
            }
        }
        return ids;
    }

    private void validateNode(Node node, Set<String> ids, List<String> errors) {
        String label = "node '" + node.id() + "'";
        if (!agentIds.contains(node.agent())) {
            errors.add(label + " references unknown agent '" + node.agent() + "'");
        }
        if (node.fallbackAgent() != null && !agentIds.contains(node.fallbackAgent())) {
            errors.add(label + " references unknown fallback agent '" + node.fallbackAgent() + "'");
        }
        if (node.maxRetries() < 0) {
            errors.add(label + " has negative maxRetries " + node.maxRetries());
        }
        for (String dependency : node.dependsOn().stream().sorted().toList()) {
            if (!ids.contains(dependency)) {
                errors.add(label + " depends on unknown node '" + dependency + "'");
            }
            if (dependency.equals(node.id())) {
                errors.add(label + " depends on itself");
            }
        }
        for (String gate : node.entryGates()) {
            if (!gateIds.contains(gate)) {
                errors.add(label + " references unknown entry gate '" + gate + "'");
            }
        }
        for (String gate : node.exitGates()) {
            if (!gateIds.contains(gate)) {
                errors.add(label + " references unknown exit gate '" + gate + "'");
            }
        }
    }

    /**
     * Kahn's algorithm finds whether a cycle exists; a DFS over the remaining nodes then names one concrete
     * cycle so the error message is actionable.
     */
    private static Optional<List<String>> findCycle(WorkflowGraph graph) {
        List<String> order = graph.topologicalOrder();
        Set<String> remaining = new HashSet<>(graph.ids());
        order.forEach(remaining::remove);
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Node> byId = new HashMap<>();
        graph.nodes().forEach(node -> byId.putIfAbsent(node.id(), node));
        String start = graph.nodes().stream().map(Node::id).filter(remaining::contains).findFirst().orElseThrow();
        List<String> path = new ArrayList<>();
        Set<String> onPath = new HashSet<>();
        String current = start;
        while (!onPath.contains(current)) {
            path.add(current);
            onPath.add(current);
            String next = byId.get(current).dependsOn().stream().filter(remaining::contains).sorted().findFirst().orElseThrow();
            current = next;
        }
        List<String> cycle = new ArrayList<>(path.subList(path.indexOf(current), path.size()));
        cycle.add(current);
        Collections.reverse(cycle);
        return Optional.of(cycle);
    }
}
