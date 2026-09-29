package com.example.agentic.core.engine;

import java.nio.file.Path;

/**
 * Layout of a run directory: {@code events.db}, {@code workspace/}, {@code staging/}, {@code pending/<node>/},
 * {@code promotions/<node>/<hash>/}, {@code artifacts/}, {@code gate-logs/}, {@code incident.md}.
 *
 * @param root absolute run directory
 */
public record RunPaths(Path root) {

    public RunPaths {
        root = root.toAbsolutePath().normalize();
    }

    public Path events() {
        return root.resolve("events.db");
    }

    public Path workspace() {
        return root.resolve("workspace");
    }

    public Path staging() {
        return root.resolve("staging");
    }

    /** Retained staged tree of a node awaiting a human decision. */
    public Path pending(String nodeId) {
        return root.resolve("pending").resolve(nodeId);
    }

    /** Pre-images recorded when a node's artifact was promoted. */
    public Path promotion(String nodeId, String artifactHash) {
        return root.resolve("promotions").resolve(nodeId).resolve(artifactHash);
    }

    public Path artifacts() {
        return root.resolve("artifacts");
    }

    public Path gateLogs() {
        return root.resolve("gate-logs");
    }

    public Path incident() {
        return root.resolve("incident.md");
    }
}
