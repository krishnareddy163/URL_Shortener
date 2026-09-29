package com.example.agentic.core.approval;

/** An approval or rejection no longer matches the artifact awaiting a decision; nothing was changed. */
public final class StaleApprovalException extends Exception {
    private static final long serialVersionUID = 1L;

    public StaleApprovalException(String message) {
        super(message);
    }
}
