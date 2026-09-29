package com.example.agentic.support;

import com.example.agentic.core.agent.WorkspaceView;

import java.util.List;
import java.util.Optional;

/** A workspace view with no files, for agent tests that do not read the workspace. */
public final class EmptyWorkspace implements WorkspaceView {
    public static final EmptyWorkspace INSTANCE = new EmptyWorkspace();

    private EmptyWorkspace() {
    }

    @Override
    public List<String> listFiles() {
        return List.of();
    }

    @Override
    public Optional<String> read(String relativePath) {
        return Optional.empty();
    }
}
