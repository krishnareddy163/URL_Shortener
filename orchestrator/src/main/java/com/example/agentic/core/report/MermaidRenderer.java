package com.example.agentic.core.report;

import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.RunState;

import java.util.Locale;
import java.util.Map;

/** Renders a workflow graph as a Mermaid {@code graph TD} block with nodes colored by status. */
public final class MermaidRenderer {
    private static final Map<NodeStatus, String> STYLES = Map.of(
            NodeStatus.DONE, "fill:#d3f9d8,stroke:#2b8a3e",
            NodeStatus.FAILED, "fill:#ffe3e3,stroke:#c92a2a",
            NodeStatus.SKIPPED, "fill:#f1f3f5,stroke:#868e96,stroke-dasharray:4",
            NodeStatus.AWAITING_APPROVAL, "fill:#fff3bf,stroke:#e67700",
            NodeStatus.AWAITING_CLARIFICATION, "fill:#fff3bf,stroke:#e67700",
            NodeStatus.RUNNING, "fill:#d0ebff,stroke:#1864ab",
            NodeStatus.PENDING, "fill:#ffffff,stroke:#495057");

    private MermaidRenderer() {
    }

    public static String render(RunState state) {
        return render(state.graph(), state.nodes());
    }

    public static String render(WorkflowGraph graph, Map<String, RunState.NodeState> nodes) {
        StringBuilder out = new StringBuilder("```mermaid\ngraph TD\n");
        for (Node node : graph.nodes()) {
            NodeStatus status = nodes.containsKey(node.id()) ? nodes.get(node.id()).status() : NodeStatus.PENDING;
            out.append("  ").append(node.id()).append("[\"").append(escape(node.id())).append("<br/>")
                    .append(escape(node.agent())).append(" · ").append(node.autonomy()).append("<br/>")
                    .append(status).append("\"]\n");
        }
        for (Node node : graph.nodes()) {
            for (String dependency : node.dependsOn().stream().sorted().toList()) {
                out.append("  ").append(dependency).append(" --> ").append(node.id()).append('\n');
            }
        }
        for (NodeStatus status : NodeStatus.values()) {
            out.append("  classDef ").append(status.name().toLowerCase(Locale.ROOT)).append(' ').append(STYLES.get(status)).append('\n');
        }
        for (Node node : graph.nodes()) {
            NodeStatus status = nodes.containsKey(node.id()) ? nodes.get(node.id()).status() : NodeStatus.PENDING;
            out.append("  class ").append(node.id()).append(' ').append(status.name().toLowerCase(Locale.ROOT)).append('\n');
        }
        return out.append("```\n").toString();
    }

    /** Mermaid entity-escapes label text so identifiers cannot break out of the quoted label. */
    static String escape(String text) {
        return text.replace("&", "#amp;").replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;");
    }
}
