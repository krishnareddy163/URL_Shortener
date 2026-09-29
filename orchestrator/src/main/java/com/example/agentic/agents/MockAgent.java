package com.example.agentic.agents;

import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Deterministic, fixture-driven agent (the default mode, R9).
 *
 * <p>Fixture layout: {@code <fixtureRoot>/<nodeId>[/fallback][/<variantKey>]/attempt<N>/} containing
 * {@code proposal.json} ({@code rationale}, {@code derivedFrom}, {@code data}) plus the proposed files
 * mirroring the workspace tree. The {@code fallback} directory is used during a fallback round when present;
 * the variant directory when the node's variant question was answered. A missing attempt directory is an
 * {@link AgentException}, which the engine counts as a failed attempt.
 */
public final class MockAgent implements Agent {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String id;
    private final Path fixtureRoot;

    public MockAgent(String id, Path fixtureRoot) {
        this.id = id;
        this.fixtureRoot = fixtureRoot.toAbsolutePath().normalize();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of("mode", "MOCK");
    }

    @Override
    public Proposal propose(AgentContext context) throws AgentException {
        Path attemptDir = resolveAttemptDir(context);
        Path proposalFile = attemptDir.resolve("proposal.json");
        if (!Files.isRegularFile(proposalFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new AgentException("fixture " + fixtureRoot.relativize(attemptDir) + " has no proposal.json");
        }
        try {
            JsonNode json = JSON.readTree(proposalFile.toFile());
            String rationale = json.path("rationale").asText("");
            List<String> derivedFrom = JSON.convertValue(json.path("derivedFrom"), JSON.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            Map<String, Object> data = json.has("data") ? JSON.convertValue(json.get("data"), JSON.getTypeFactory()
                    .constructMapType(TreeMap.class, String.class, Object.class)) : Map.of();
            return new Proposal(readFiles(attemptDir), rationale, derivedFrom, data);
        } catch (IOException | IllegalArgumentException exception) {
            throw new AgentException("cannot read fixture " + proposalFile + ": " + exception.getMessage(), exception);
        }
    }

    private Path resolveAttemptDir(AgentContext context) throws AgentException {
        Path nodeDir = fixtureRoot.resolve(context.nodeId());
        if (context.fallbackRound() && Files.isDirectory(nodeDir.resolve("fallback"))) {
            nodeDir = nodeDir.resolve("fallback");
        }
        Path variantDir = nodeDir.resolve(context.variantKey());
        Path base;
        if (Files.isDirectory(variantDir)) {
            base = variantDir;
        } else if (context.variantKey().equals("default")) {
            base = nodeDir;
        } else {
            throw new AgentException("no fixture variant '" + context.variantKey() + "' for node " + context.nodeId());
        }
        Path attemptDir = base.resolve("attempt" + context.attempt()).normalize();
        if (!attemptDir.startsWith(fixtureRoot)) {
            throw new AgentException("fixture path escapes the fixture root");
        }
        if (!Files.isDirectory(attemptDir)) {
            throw new AgentException("no fixture for attempt " + context.attempt() + " (" + fixtureRoot.relativize(attemptDir) + ")");
        }
        return attemptDir;
    }

    private static Map<String, String> readFiles(Path attemptDir) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(attemptDir)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = attemptDir.relativize(path).toString().replace('\\', '/');
                if (!relative.equals("proposal.json") && !relative.endsWith(".DS_Store")) {
                    files.put(relative, Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }
}
