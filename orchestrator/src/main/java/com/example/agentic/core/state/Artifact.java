package com.example.agentic.core.state;

import java.util.List;
import java.util.Map;

/**
 * A content-addressed node output.
 *
 * @param hash        {@link Hashing#artifactHash} of {@code files} and {@code data}
 * @param nodeId      producing node
 * @param derivedFrom lineage: {@code requirement} or {@code nodeId@artifactHash} entries
 * @param rationale   the agent's explanation (stored, not hashed)
 * @param files       proposed files keyed by workspace-relative path
 * @param data        structured output (ambiguities, impact report, findings, graph patch, ...)
 */
public record Artifact(
        String hash,
        String nodeId,
        List<String> derivedFrom,
        String rationale,
        Map<String, String> files,
        Map<String, Object> data) {

    public Artifact {
        derivedFrom = derivedFrom == null ? List.of() : List.copyOf(derivedFrom);
        files = Map.copyOf(ImmutableMaps.withoutNulls(files));
        data = Map.copyOf(ImmutableMaps.withoutNulls(data));
    }

    /** Builds an artifact, computing its hash from files and data. */
    public static Artifact of(String nodeId, List<String> derivedFrom, String rationale,
                              Map<String, String> files, Map<String, Object> data) {
        Map<String, String> safeFiles = Map.copyOf(ImmutableMaps.withoutNulls(files));
        Map<String, Object> safeData = Map.copyOf(ImmutableMaps.withoutNulls(data));
        return new Artifact(Hashing.artifactHash(safeFiles, safeData), nodeId, derivedFrom, rationale,
                safeFiles, safeData);
    }
}
