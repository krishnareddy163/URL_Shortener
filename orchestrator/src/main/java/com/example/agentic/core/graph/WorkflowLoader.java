package com.example.agentic.core.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Parses workflow YAML and rejects invalid graphs before anything runs (unknown fields are errors, so a
 * typo such as {@code dependOn} cannot silently remove a dependency).
 */
public final class WorkflowLoader {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    private final GraphValidator validator;

    public WorkflowLoader(GraphValidator validator) {
        this.validator = validator;
    }

    public WorkflowGraph load(Path file) throws IOException, WorkflowValidationException {
        if (!Files.isRegularFile(file)) {
            throw new WorkflowValidationException(List.of("workflow file not found: " + file));
        }
        return parse(Files.readString(file));
    }

    public WorkflowGraph parse(String yamlText) throws WorkflowValidationException {
        WorkflowGraph graph;
        try {
            graph = yaml.readValue(yamlText, WorkflowGraph.class);
        } catch (UnrecognizedPropertyException exception) {
            throw new WorkflowValidationException(List.of("unknown field '" + exception.getPropertyName() + "'"));
        } catch (JsonProcessingException exception) {
            throw new WorkflowValidationException(List.of("workflow YAML is malformed: " + exception.getOriginalMessage()));
        }
        if (graph == null) {
            throw new WorkflowValidationException(List.of("workflow YAML is empty"));
        }
        validator.validateOrThrow(graph);
        return graph;
    }
}
