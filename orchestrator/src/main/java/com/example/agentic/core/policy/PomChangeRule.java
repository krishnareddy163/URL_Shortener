package com.example.agentic.core.policy;

import com.example.agentic.core.workspace.Diff;

import java.util.Optional;

/** Escalates any build file change (dependencies and plugins alter the supply chain). */
public final class PomChangeRule implements RiskRule {

    @Override
    public Optional<String> flag(Diff diff) {
        return diff.paths().stream()
                .filter(path -> path.equals("pom.xml") || path.endsWith("/pom.xml"))
                .findFirst()
                .map(path -> "PomChangeRule: build file changed (" + path + ")");
    }
}
