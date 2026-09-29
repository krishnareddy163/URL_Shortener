package com.example.agentic.core.graph;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

/**
 * One unit of work in a workflow graph: which agent runs, what it waits for, and how it is governed.
 *
 * @param id                 unique node id
 * @param agent              agent id that produces the node's proposal
 * @param dependsOn          ids of nodes that must be DONE first
 * @param entryGates         gates evaluated before any agent call; a failure is blocking
 * @param exitGates          gates evaluated in order against the staged proposal
 * @param autonomy           oversight level
 * @param maxRetries         extra attempts allowed per agent (2 means at most 3 attempts)
 * @param fallbackAgent      agent used for one round after retries are exhausted, or {@code null}
 * @param fixtureVariantFrom question id whose answer selects the fixture variant, or {@code null}
 * @param task               what this node must achieve, stated to live agents; tells apart nodes that share an
 *                           agent (for example a behavior-preserving refactor and the fix). Only a human-authored
 *                           workflow may set it, never a graph patch. {@code null} if not given
 */
public record Node(
        String id,
        String agent,
        Set<String> dependsOn,
        List<String> entryGates,
        List<String> exitGates,
        Autonomy autonomy,
        int maxRetries,
        String fallbackAgent,
        String fixtureVariantFrom,
        String task) {

    public static final int DEFAULT_MAX_RETRIES = 2;

    public Node {
        dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
        entryGates = entryGates == null ? List.of() : List.copyOf(entryGates);
        exitGates = exitGates == null ? List.of() : List.copyOf(exitGates);
        autonomy = autonomy == null ? Autonomy.AUTO : autonomy;
        task = task == null || task.isBlank() ? null : task.strip();
    }

    /** A node without a task description. */
    public Node(String id, String agent, Set<String> dependsOn, List<String> entryGates, List<String> exitGates,
                Autonomy autonomy, int maxRetries, String fallbackAgent, String fixtureVariantFrom) {
        this(id, agent, dependsOn, entryGates, exitGates, autonomy, maxRetries, fallbackAgent, fixtureVariantFrom, null);
    }

    /** YAML/JSON factory applying defaults for omitted optional fields. */
    @JsonCreator
    static Node fromJson(@JsonProperty("id") String id,
                         @JsonProperty("agent") String agent,
                         @JsonProperty("dependsOn") Set<String> dependsOn,
                         @JsonProperty("entryGates") List<String> entryGates,
                         @JsonProperty("exitGates") List<String> exitGates,
                         @JsonProperty("autonomy") Autonomy autonomy,
                         @JsonProperty("maxRetries") Integer maxRetries,
                         @JsonProperty("fallbackAgent") String fallbackAgent,
                         @JsonProperty("fixtureVariantFrom") String fixtureVariantFrom,
                         @JsonProperty("task") String task) {
        return new Node(id, agent, dependsOn, entryGates, exitGates, autonomy,
                maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries, fallbackAgent, fixtureVariantFrom, task);
    }

    public Node withDependsOn(Set<String> newDependencies) {
        return new Node(id, agent, newDependencies, entryGates, exitGates, autonomy, maxRetries, fallbackAgent,
                fixtureVariantFrom, task);
    }

    public Node withFallbackAgent(String newFallbackAgent) {
        return new Node(id, agent, dependsOn, entryGates, exitGates, autonomy, maxRetries, newFallbackAgent,
                fixtureVariantFrom, task);
    }
}
