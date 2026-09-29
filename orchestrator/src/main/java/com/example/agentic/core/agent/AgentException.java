package com.example.agentic.core.agent;

/** An agent could not produce a usable proposal; the engine counts it as a failed attempt. */
public class AgentException extends Exception {
    private static final long serialVersionUID = 1L;

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
