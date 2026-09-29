package com.example.agentic.core.workspace;

import com.example.agentic.core.state.Hashing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Filesystem helpers for workspace trees. Build output ({@code target/}) and VCS metadata are ignored. */
public final class FileTrees {
    public static final String ABSENT = "absent";
    private static final Set<String> IGNORED_SEGMENTS = Set.of("target", ".git", ".DS_Store");

    private FileTrees() {
    }

    /** Parent directory of an absolute or relative file path; fails fast for a root path. */
    public static Path parentOf(Path file) {
        return Objects.requireNonNull(file.toAbsolutePath().getParent(), () -> "path has no parent: " + file);
    }

    public static boolean ignored(Path relative) {
        for (Path segment : relative) {
            if (IGNORED_SEGMENTS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Sorted workspace-relative file paths (forward slashes), excluding ignored segments. */
    public static List<String> listFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                Path relative = root.relativize(path);
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !ignored(relative)) {
                    files.add(relative.toString().replace('\\', '/'));
                }
            }
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    /** Copies a tree, refusing symlinks and skipping ignored segments. */
    public static void copyTree(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isEmpty() || ignored(relative)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("refusing to copy symlink " + relative);
                }
                Path target = destination.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(parentOf(target));
                    Files.copy(path, target);
                }
            }
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Hash of all file paths and contents under {@code root}; equal hashes mean byte-identical trees. */
    public static String treeHash(Path root) throws IOException {
        StringBuilder manifest = new StringBuilder();
        for (String file : listFiles(root)) {
            manifest.append(file).append('\0').append(Hashing.sha256(Files.readAllBytes(root.resolve(file)))).append('\n');
        }
        return Hashing.sha256(manifest.toString());
    }

    /** Content hash of one file, or {@link #ABSENT}. */
    public static String fileHash(Path file) throws IOException {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Hashing.sha256(Files.readAllBytes(file)) : ABSENT;
    }

    public static String readOrNull(Path file) throws IOException {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Files.readString(file, StandardCharsets.UTF_8) : null;
    }
}
