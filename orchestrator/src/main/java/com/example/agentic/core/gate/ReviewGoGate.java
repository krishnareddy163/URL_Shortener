package com.example.agentic.core.gate;

import com.example.agentic.core.state.Artifact;

import java.util.List;

/**
 * Release-readiness entry gate: at least one upstream artifact carries a review {@code recommendation},
 * and every such recommendation is {@code GO}.
 */
public final class ReviewGoGate implements Gate {
    private static final String RECOMMENDATION = "recommendation";

    @Override
    public String id() {
        return "review-go";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        List<Artifact> reviews = context.upstream().values().stream()
                .filter(artifact -> artifact.data().containsKey(RECOMMENDATION))
                .toList();
        if (reviews.isEmpty()) {
            return GateResult.fail(id(), "no upstream review recommendation found");
        }
        for (Artifact review : reviews) {
            if (!"GO".equals(review.data().get(RECOMMENDATION))) {
                return GateResult.fail(id(), "review " + review.nodeId() + " recommends "
                        + review.data().get(RECOMMENDATION));
            }
        }
        return GateResult.pass();
    }
}
