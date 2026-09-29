package com.example.agentic.core.gate;

import java.util.List;

/**
 * Compliance gate: every proposal carries a rationale and real lineage. {@code derivedFrom} must be
 * non-empty (except for the {@code requirements} agent, which derives from the raw requirement) and each
 * entry must be {@code requirement} or one of the node's transitive dependencies.
 */
public final class ArtifactMetadataGate implements Gate {
    public static final String REQUIREMENT = "requirement";

    @Override
    public String id() {
        return "artifact-metadata";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.isEntry()) {
            return GateResult.fail(id(), "artifact-metadata is an exit gate and needs a proposal");
        }
        String rationale = context.proposal().rationale();
        if (rationale == null || rationale.isBlank()) {
            return GateResult.fail(id(), "proposal has no rationale");
        }
        List<String> derivedFrom = context.proposal().derivedFrom();
        if (derivedFrom.isEmpty() && !context.node().agent().equals("requirements")) {
            return GateResult.fail(id(), "proposal has no derivedFrom lineage");
        }
        for (String source : derivedFrom) {
            if (!source.equals(REQUIREMENT) && !context.upstream().containsKey(source)) {
                return GateResult.fail(id(), "derivedFrom '" + source + "' is neither 'requirement' nor an upstream node "
                        + context.upstream().keySet());
            }
        }
        return GateResult.pass();
    }
}
