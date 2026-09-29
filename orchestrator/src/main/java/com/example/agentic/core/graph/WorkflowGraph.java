package com.example.agentic.core.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable workflow definition: the requirement, the starting workspace, run budgets and the node DAG.
 *
 * @param name        workflow (scenario) name
 * @param requirement the natural-language requirement driving the run
 * @param workspace   {@code empty} or {@code copy:<repo-relative path>}
 * @param budgets     optional overrides of run budgets ({@code maxTotalAttempts}, ...)
 * @param nodes       nodes in declaration order
 */
public record WorkflowGraph(
        String name,
        String requirement,
        String workspace,
        Map<String, Integer> budgets,
        List<Node> nodes) {

    public WorkflowGraph {
        workspace = workspace == null ? "empty" : workspace;
        budgets = budgets == null ? Map.of() : Map.copyOf(budgets);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public Optional<Node> node(String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst();
    }

    public Node require(String id) {
        return node(id).orElseThrow(() -> new IllegalArgumentException("unknown node: " + id));
    }

    public Set<String> ids() {
        Set<String> ids = new LinkedHashSet<>();
        nodes.forEach(node -> ids.add(node.id()));
        return ids;
    }

    public WorkflowGraph withNodes(List<Node> newNodes) {
        return new WorkflowGraph(name, requirement, workspace, budgets, newNodes);
    }

    /** Ids of nodes that directly depend on {@code id}. */
    public Set<String> dependentsOf(String id) {
        Set<String> dependents = new TreeSet<>();
        nodes.stream().filter(node -> node.dependsOn().contains(id)).forEach(node -> dependents.add(node.id()));
        return dependents;
    }

    /** All nodes reachable downstream of {@code id}, in topological order. */
    public List<String> downstreamOf(String id) {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>(dependentsOf(id));
        while (!frontier.isEmpty()) {
            String next = frontier.removeFirst();
            if (reached.add(next)) {
                frontier.addAll(dependentsOf(next));
            }
        }
        return topologicalOrder().stream().filter(reached::contains).toList();
    }

    /** All transitive dependencies of {@code id}, in topological order. */
    public List<String> upstreamOf(String id) {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>(require(id).dependsOn());
        while (!frontier.isEmpty()) {
            String next = frontier.removeFirst();
            if (reached.add(next)) {
                node(next).ifPresent(node -> frontier.addAll(node.dependsOn()));
            }
        }
        return topologicalOrder().stream().filter(reached::contains).toList();
    }

    /**
     * Deterministic topological order (Kahn's algorithm; ties broken by declaration order).
     * Nodes on a cycle are omitted; {@link GraphValidator} reports cycles.
     */
    public List<String> topologicalOrder() {
        Map<String, Integer> declared = new HashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            declared.put(node.id(), index);
            indegree.put(node.id(), 0);
        }
        for (Node node : nodes) {
            for (String dependency : node.dependsOn()) {
                if (indegree.containsKey(dependency)) {
                    indegree.merge(node.id(), 1, Integer::sum);
                }
            }
        }
        TreeSet<String> ready = new TreeSet<>((left, right) -> Integer.compare(declared.get(left), declared.get(right)));
        indegree.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String next = ready.pollFirst();
            order.add(next);
            for (String dependent : dependentsOf(next)) {
                if (indegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        return order;
    }
}
