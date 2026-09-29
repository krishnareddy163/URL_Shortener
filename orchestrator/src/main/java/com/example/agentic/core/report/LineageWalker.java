package com.example.agentic.core.report;

import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.Hashing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks {@code derivedFrom} links from an artifact back to the requirement, producing an indented decision
 * chain: each line is a node, its artifact hash and the agent's rationale.
 */
public final class LineageWalker {
    private final ArtifactStore artifacts;

    public LineageWalker(ArtifactStore artifacts) {
        this.artifacts = artifacts;
    }

    public List<String> chain(String artifactHash, String requirement) {
        List<String> lines = new ArrayList<>();
        walk(artifactHash, requirement, 0, new HashSet<>(), lines);
        return lines;
    }

    private void walk(String hash, String requirement, int depth, Set<String> visited, List<String> lines) {
        String indent = "  ".repeat(depth);
        Optional<Artifact> found = artifacts.get(hash);
        if (found.isEmpty()) {
            lines.add(indent + "- artifact " + Hashing.shortHash(hash) + " (not stored)");
            return;
        }
        Artifact artifact = found.get();
        if (!visited.add(hash)) {
            lines.add(indent + "- **" + artifact.nodeId() + "** `" + Hashing.shortHash(hash) + "` (see above)");
            return;
        }
        lines.add(indent + "- **" + artifact.nodeId() + "** `" + Hashing.shortHash(hash) + "`: " + oneLine(artifact.rationale()));
        if (artifact.derivedFrom().isEmpty()) {
            lines.add(indent + "  - requirement: \"" + oneLine(requirement) + "\"");
        }
        for (String source : artifact.derivedFrom()) {
            int at = source.indexOf('@');
            if (at > 0) {
                walk(source.substring(at + 1), requirement, depth + 1, visited, lines);
            } else {
                lines.add(indent + "  - requirement: \"" + oneLine(requirement) + "\"");
            }
        }
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > 160 ? flat.substring(0, 157) + "..." : flat;
    }
}
