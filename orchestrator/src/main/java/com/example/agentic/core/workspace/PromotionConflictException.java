package com.example.agentic.core.workspace;

/**
 * A promoted file changed between staging and promotion (or between promotion and revert). This is a hard
 * failure: the engine never overwrites work it did not stage from.
 */
public final class PromotionConflictException extends Exception {
    private static final long serialVersionUID = 1L;

    public PromotionConflictException(String message) {
        super(message);
    }
}
