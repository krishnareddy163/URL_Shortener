package com.example.agentic.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathGuardTest {
    private static final List<String> SCOPE = List.of("src/main/**", "pom.xml");
    private final PathGuard guard = new PathGuard();

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"../outside.txt", "src/main/../../etc/passwd", "src/main/java/../../../x"})
    void rejectsParentTraversal(String path) {
        assertThatThrownBy(() -> guard.check(root, path, SCOPE)).isInstanceOf(PathViolationException.class)
                .hasMessageContaining("traversal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "C:/Windows/system.ini"})
    void rejectsAbsolutePaths(String path) {
        assertThatThrownBy(() -> guard.check(root, path, SCOPE)).isInstanceOf(PathViolationException.class)
                .hasMessageContaining("absolute");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "src//main/A.java", "./pom.xml", "src\\main\\A.java"})
    void rejectsMalformedPaths(String path) {
        assertThatThrownBy(() -> guard.check(root, path, SCOPE)).isInstanceOf(PathViolationException.class);
    }

    @Test
    void rejectsSymlinksAlongThePath(@TempDir Path outside) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createSymbolicLink(root.resolve("src/main"), outside);

        assertThatThrownBy(() -> guard.check(root, "src/main/Evil.java", SCOPE))
                .isInstanceOf(PathViolationException.class).hasMessageContaining("symlink");
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/test/java/A.java", "README.md", "src/mainframe/A.java", ".github/workflows/ci.yml"})
    void rejectsOutOfScopePaths(String path) {
        assertThatThrownBy(() -> guard.check(root, path, SCOPE)).isInstanceOf(PathViolationException.class)
                .hasMessageContaining("outside the agent's allowed scope");
    }

    @Test
    void acceptsInScopePaths() throws Exception {
        assertThat(guard.check(root, "src/main/java/com/example/A.java", SCOPE)).isEqualTo("src/main/java/com/example/A.java");
        assertThat(guard.check(root, "pom.xml", SCOPE)).isEqualTo("pom.xml");
    }

    @ParameterizedTest
    @ValueSource(strings = {".git/config", "src/main/.GIT/hooks/pre-commit", "a/.git"})
    void theWorkspaceGitHistoryIsOffLimitsEvenWithAWildcardScope(String path) {
        assertThatThrownBy(() -> guard.check(root, path, List.of("**"))).isInstanceOf(PathViolationException.class)
                .hasMessageContaining("engine-owned");
    }
}
