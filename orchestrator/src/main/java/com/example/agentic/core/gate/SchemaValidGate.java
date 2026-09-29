package com.example.agentic.core.gate;

import com.example.agentic.core.workspace.FileTrees;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quality gate over the staged tree: {@code openapi.yaml} must exist, parse, define paths, and give every
 * operation at least one response; every file under {@code db/migration/} must be a non-empty
 * {@code V<n>__<name>.sql} with a unique version.
 */
public final class SchemaValidGate implements Gate {
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    private static final Pattern MIGRATION = Pattern.compile("V(\\d+)__\\w+\\.sql");
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Override
    public String id() {
        return "schema-valid";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.staging() == null) {
            return GateResult.fail(id(), "schema-valid is an exit gate and needs a staged proposal");
        }
        try {
            GateResult openApi = checkOpenApi(context.staging().resolve("openapi.yaml"));
            return openApi.passed() ? checkMigrations(context.staging()) : openApi;
        } catch (IOException exception) {
            return GateResult.fail(id(), "cannot read staged files: " + exception.getMessage());
        }
    }

    private GateResult checkOpenApi(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return GateResult.fail(id(), "openapi.yaml is missing");
        }
        JsonNode root;
        try {
            root = yaml.readTree(Files.readString(file));
        } catch (IOException exception) {
            return GateResult.fail(id(), "openapi.yaml does not parse: " + exception.getMessage().lines().findFirst().orElse(""));
        }
        JsonNode paths = root == null ? null : root.get("paths");
        if (paths == null || !paths.isObject() || paths.isEmpty()) {
            return GateResult.fail(id(), "openapi.yaml defines no paths");
        }
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            GateResult pathResult = checkPath(path.getKey(), path.getValue());
            if (!pathResult.passed()) {
                return pathResult;
            }
        }
        return GateResult.pass();
    }

    private GateResult checkPath(String path, JsonNode item) {
        boolean hasOperation = false;
        for (Map.Entry<String, JsonNode> operation : item.properties()) {
            if (METHODS.contains(operation.getKey())) {
                hasOperation = true;
                JsonNode responses = operation.getValue().get("responses");
                if (responses == null || !responses.isObject() || responses.isEmpty()) {
                    return GateResult.fail(id(), operation.getKey().toUpperCase(Locale.ROOT) + " " + path + " has no responses");
                }
            }
        }
        return hasOperation ? GateResult.pass() : GateResult.fail(id(), "path " + path + " has no operations");
    }

    private GateResult checkMigrations(Path staging) throws IOException {
        Set<Integer> versions = new HashSet<>();
        for (String relative : FileTrees.listFiles(staging)) {
            if (!relative.contains("db/migration/")) {
                continue;
            }
            String name = relative.substring(relative.lastIndexOf('/') + 1);
            Matcher matcher = MIGRATION.matcher(name);
            if (!matcher.matches()) {
                return GateResult.fail(id(), "migration file name must match V<n>__<name>.sql: " + relative);
            }
            if (Files.readString(staging.resolve(relative)).isBlank()) {
                return GateResult.fail(id(), "migration is empty: " + relative);
            }
            if (!versions.add(Integer.parseInt(matcher.group(1)))) {
                return GateResult.fail(id(), "duplicate migration version V" + matcher.group(1));
            }
        }
        return GateResult.pass();
    }
}
