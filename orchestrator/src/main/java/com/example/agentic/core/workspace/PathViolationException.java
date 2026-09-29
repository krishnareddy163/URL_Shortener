package com.example.agentic.core.workspace;

/** A proposed path is unsafe (traversal, absolute, symlink) or outside the agent's allowed scope. */
public final class PathViolationException extends Exception {
    private static final long serialVersionUID = 1L;

    public PathViolationException(String message) {
        super(message);
    }
}
