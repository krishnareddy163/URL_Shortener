package com.example.agentic.core.agent;

import com.example.agentic.core.state.ImmutableMaps;

import java.util.List;
import java.util.Map;

/**
 * What an agent returns. Agents never write files; the engine validates and applies {@code files}.
 *
 * @param files       proposed file contents keyed by workspace-relative path
 * @param rationale   why the agent made these changes
 * @param derivedFrom lineage: {@code requirement} and/or upstream node ids
 * @param data        structured outputs (ambiguities, impact report, graph patch, findings, ...)
 */
public record Proposal(Map<String, String> files, String rationale, List<String> derivedFrom, Map<String, Object> data) {

    public Proposal {
        files = Map.copyOf(ImmutableMaps.withoutNulls(files));
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
        data = Map.copyOf(ImmutableMaps.withoutNulls(data));
    }
}
