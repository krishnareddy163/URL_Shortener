package com.example.agentic.core.policy;

import com.example.agentic.core.workspace.Diff;
import com.example.agentic.core.workspace.PathGuard;

import java.util.List;
import java.util.Optional;

/** Escalates any change to a schema migration (irreversible once applied to a real database). */
public final class MigrationPathRule implements RiskRule {
    private final List<String> globs;

    public MigrationPathRule(List<String> globs) {
        this.globs = List.copyOf(globs);
    }

    @Override
    public Optional<String> flag(Diff diff) {
        return diff.paths().stream()
                .filter(path -> PathGuard.matchesAny(path, globs))
                .findFirst()
                .map(path -> "MigrationPathRule: schema migration changed (" + path + ")");
    }
}
