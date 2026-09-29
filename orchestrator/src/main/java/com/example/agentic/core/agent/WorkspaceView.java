package com.example.agentic.core.agent;

import java.util.List;
import java.util.Optional;

/** Read-only view of the promoted workspace handed to agents (for example for codebase analysis). */
public interface WorkspaceView {

    /** Workspace-relative paths of all files, sorted, excluding build output. */
    List<String> listFiles();

    Optional<String> read(String relativePath);
}
