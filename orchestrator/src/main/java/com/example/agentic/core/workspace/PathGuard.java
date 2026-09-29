package com.example.agentic.core.workspace;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates every path an agent proposes before anything is written: no blank or NUL paths, no absolute
 * or drive paths, no {@code ..} or {@code .} segments, no symlinks along the path, and a match against
 * at least one of the agent's allowlisted globs.
 */
public final class PathGuard {
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");
    /** ASCII-only, case-insensitive: {@code .git} in any case (case-insensitive file systems). */
    private static final Pattern GIT_DIRECTORY = Pattern.compile("\\.git", Pattern.CASE_INSENSITIVE);

    /**
     * Returns the normalized relative path (forward slashes).
     *
     * @throws PathViolationException describing the first violated rule
     */
    public String check(Path root, String proposed, List<String> allowedGlobs) throws PathViolationException {
        checkSyntax(proposed);
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(proposed).normalize();
        if (!target.startsWith(base)) {
            throw new PathViolationException("path escapes the workspace: " + proposed);
        }
        checkNoSymlinks(base, target, proposed);
        if (!matchesAny(proposed, allowedGlobs)) {
            throw new PathViolationException("path is outside the agent's allowed scope " + allowedGlobs + ": " + proposed);
        }
        return proposed;
    }

    private static void checkSyntax(String proposed) throws PathViolationException {
        if (proposed == null || proposed.isBlank() || proposed.indexOf('\0') >= 0) {
            throw new PathViolationException("path must be non-blank without NUL characters");
        }
        if (proposed.contains("\\")) {
            throw new PathViolationException("backslashes are not allowed: " + proposed);
        }
        if (proposed.startsWith("/") || DRIVE.matcher(proposed).matches() || Path.of(proposed).isAbsolute()) {
            throw new PathViolationException("absolute paths are not allowed: " + proposed);
        }
        for (String segment : proposed.split("/", -1)) {
            if (segment.equals("..")) {
                throw new PathViolationException("parent traversal is not allowed: " + proposed);
            }
            if (segment.isEmpty() || segment.equals(".")) {
                throw new PathViolationException("empty or '.' path segments are not allowed: " + proposed);
            }
            if (GIT_DIRECTORY.matcher(segment).matches()) {
                throw new PathViolationException("the workspace git history is engine-owned: " + proposed);
            }
        }
    }

    private static void checkNoSymlinks(Path base, Path target, String proposed) throws PathViolationException {
        Path current = base;
        for (Path segment : base.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new PathViolationException("symlinks are not allowed: " + proposed);
            }
        }
    }

    public static boolean matchesAny(String relativePath, List<String> globs) {
        Path path = Path.of(relativePath);
        for (String glob : globs) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            if (matcher.matches(path)) {
                return true;
            }
        }
        return false;
    }
}
