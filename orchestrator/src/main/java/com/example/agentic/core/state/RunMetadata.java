package com.example.agentic.core.state;

/**
 * Provenance of a run recorded in {@code RUN_STARTED}, so any process can reopen it with the same agents.
 *
 * @param mode          agent mode ({@code MOCK} or {@code LIVE})
 * @param workflowFile  workflow YAML the run was started from
 * @param fixtures      fixture root used by fixture-driven agents
 * @param policiesHash  SHA-256 of the policy file in force at start
 */
public record RunMetadata(String mode, String workflowFile, String fixtures, String policiesHash) {

    /** Metadata for runs started programmatically (tests, embedded use). */
    public static RunMetadata none() {
        return new RunMetadata("MOCK", null, null, null);
    }
}
