package com.example.agentic.core.policy;

import com.example.agentic.core.workspace.Diff;

import java.util.Optional;

/** Escalates any file deletion. */
public final class DeletionRule implements RiskRule {

    @Override
    public Optional<String> flag(Diff diff) {
        return diff.changes().stream()
                .filter(change -> change.type() == Diff.ChangeType.DELETED)
                .findFirst()
                .map(change -> "DeletionRule: file deleted (" + change.path() + ")");
    }
}
