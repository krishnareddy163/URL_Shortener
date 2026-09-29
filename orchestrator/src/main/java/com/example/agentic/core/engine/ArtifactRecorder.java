package com.example.agentic.core.engine;

import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.ArtifactStore;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.workspace.FileTrees;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** Stores artifacts in the run's content-addressed store and exports a readable JSON copy under {@code artifacts/}. */
public final class ArtifactRecorder {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ArtifactStore store;
    private final RunPaths paths;

    public ArtifactRecorder(ArtifactStore store, RunPaths paths) {
        this.store = store;
        this.paths = paths;
    }

    public void save(Artifact artifact) {
        store.put(artifact);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("hash", artifact.hash());
        export.put("nodeId", artifact.nodeId());
        export.put("derivedFrom", artifact.derivedFrom());
        export.put("rationale", artifact.rationale());
        export.put("files", new TreeSet<>(artifact.files().keySet()));
        export.put("data", artifact.data());
        Path file = paths.artifacts().resolve(artifact.nodeId() + "-" + Hashing.shortHash(artifact.hash()) + ".json");
        try {
            Files.createDirectories(FileTrees.parentOf(file));
            JSON.writeValue(file.toFile(), export);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Optional<Artifact> find(String hash) {
        return hash == null ? Optional.empty() : store.get(hash);
    }

    /** The artifact with this hash; its absence means the store is corrupt. */
    public Artifact require(String hash) {
        return find(hash).orElseThrow(() -> new IllegalStateException("artifact " + hash + " is missing from the store"));
    }
}
