package com.example.agentic.core.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A disposable copy of the promoted workspace in which one attempt's proposal is applied and gated.
 * Failed attempts {@link #discard()} it; the promoted workspace is never touched until promotion.
 */
public final class Staging {
    private final Path path;
    private final Map<String, String> baseHashes = new TreeMap<>();
    private final Map<String, String> baseContents = new HashMap<>();
    private final Map<String, String> applied = new LinkedHashMap<>();

    Staging(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    /**
     * Validates every path first (all-or-nothing), records each file's base content and hash, then writes.
     */
    public void apply(Map<String, String> files, PathGuard guard, List<String> allowedGlobs)
            throws PathViolationException, IOException {
        for (String relative : files.keySet()) {
            guard.check(path, relative, allowedGlobs);
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = path.resolve(file.getKey());
            baseHashes.put(file.getKey(), FileTrees.fileHash(target));
            baseContents.put(file.getKey(), FileTrees.readOrNull(target));
            Files.createDirectories(FileTrees.parentOf(target));
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            applied.put(file.getKey(), file.getValue());
        }
    }

    /** Hash of each proposed path in the workspace at staging time ({@link FileTrees#ABSENT} if new). */
    public Map<String, String> baseHashes() {
        return Collections.unmodifiableMap(baseHashes);
    }

    public Diff diff() {
        return Diff.of(baseContents, applied);
    }

    public void discard() throws IOException {
        FileTrees.deleteTree(path);
    }

    /** Moves the staged tree to {@code destination} so it can be promoted after a human decision. */
    public Path retainAt(Path destination) throws IOException {
        FileTrees.deleteTree(destination);
        Files.createDirectories(FileTrees.parentOf(destination));
        Files.move(path, destination, StandardCopyOption.ATOMIC_MOVE);
        return destination;
    }
}
