package com.example.agentic.core.graph;

import java.util.ArrayList;
import java.util.List;

/** Raised when a workflow or graph patch is structurally invalid; carries every problem found. */
public final class WorkflowValidationException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ArrayList<String> errors;

    public WorkflowValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = new ArrayList<>(errors);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }
}
