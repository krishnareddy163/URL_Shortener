package com.example.agentic.core.gate;

import java.util.List;
import java.util.Map;

/**
 * Design exit gate: the proposal writes at least one design document ({@code docs/design*.md}), and every design
 * document it writes contains at least one Mermaid diagram, so each design is reviewed with a picture of its
 * components or flow.
 */
public final class DesignDiagramsGate implements Gate {
    static final String DIAGRAM = "```mermaid";

    @Override
    public String id() {
        return "design-diagrams";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.isEntry()) {
            return GateResult.fail(id(), "design-diagrams is an exit gate and needs a proposal");
        }
        List<Map.Entry<String, String>> designs = context.proposal().files().entrySet().stream()
                .filter(file -> isDesignDocument(file.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        if (designs.isEmpty()) {
            return GateResult.fail(id(), "no design document (docs/design*.md) in the proposal");
        }
        for (Map.Entry<String, String> design : designs) {
            if (!design.getValue().contains(DIAGRAM)) {
                return GateResult.fail(id(), design.getKey() + " has no Mermaid diagram (a ```mermaid block)");
            }
        }
        return GateResult.pass();
    }

    static boolean isDesignDocument(String path) {
        return path.startsWith("docs/design") && path.endsWith(".md") && path.indexOf('/', "docs/".length()) < 0;
    }
}
