package com.example.agentic.core.engine;

/** How a run invocation ended, with the CLI exit code for each case. */
public enum RunOutcome {
    COMPLETED(0),
    PAUSED(10),
    SAFE_STOPPED(20);

    private final int exitCode;

    RunOutcome(int exitCode) {
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
