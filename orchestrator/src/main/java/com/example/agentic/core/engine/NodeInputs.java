package com.example.agentic.core.engine;

import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.Artifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed inputs of one node execution.
 *
 * @param node      node being executed
 * @param variant   fixture variant selected by clarification answers
 * @param inputHash hash of the upstream artifacts and variant
 * @param upstream  current artifacts of the node's transitive dependencies, in topological order
 */
record NodeInputs(Node node, String variant, String inputHash, Map<String, Artifact> upstream) {

    NodeInputs {
        upstream = Collections.unmodifiableMap(new LinkedHashMap<>(upstream));
    }
}
