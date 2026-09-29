package com.example.agentic.core.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A structural change to the workflow proposed by an agent ({@code data.graphPatch}).
 * Edges point from dependency to dependent. Application is idempotent: re-applying a patch whose
 * nodes and edges are already present changes nothing.
 *
 * @param addNodes    nodes to insert
 * @param removeEdges dependency edges to remove
 * @param addEdges    dependency edges to add
 * @param reason      why the agent wants the change (recorded in the REPLAN event)
 */
public record GraphPatch(List<Node> addNodes, List<Edge> removeEdges, List<Edge> addEdges, String reason) {

    public GraphPatch {
        addNodes = addNodes == null ? List.of() : List.copyOf(addNodes);
        removeEdges = removeEdges == null ? List.of() : List.copyOf(removeEdges);
        addEdges = addEdges == null ? List.of() : List.copyOf(addEdges);
    }

    /**
     * A dependency edge.
     *
     * @param from the dependency
     * @param to   the dependent node
     */
    public record Edge(String from, String to) {
    }

    /** Returns the graph with this patch applied; does not validate (see {@code ReplanService}). */
    public WorkflowGraph applyTo(WorkflowGraph graph) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        for (Node added : addNodes) {
            nodes.putIfAbsent(added.id(), added);
        }
        for (Edge edge : removeEdges) {
            Node target = nodes.get(edge.to());
            if (target != null) {
                Set<String> dependencies = new HashSet<>(target.dependsOn());
                dependencies.remove(edge.from());
                nodes.put(target.id(), target.withDependsOn(dependencies));
            }
        }
        for (Edge edge : addEdges) {
            Node target = nodes.get(edge.to());
            if (target != null) {
                Set<String> dependencies = new HashSet<>(target.dependsOn());
                dependencies.add(edge.from());
                nodes.put(target.id(), target.withDependsOn(dependencies));
            }
        }
        return graph.withNodes(new ArrayList<>(nodes.values()));
    }
}
