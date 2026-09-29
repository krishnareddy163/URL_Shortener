package com.example.agentic.core.state;

import java.util.Optional;

/** Content-addressed storage for node artifacts; storing the same hash twice is a no-op. */
public interface ArtifactStore {

    void put(Artifact artifact);

    Optional<Artifact> get(String hash);
}
