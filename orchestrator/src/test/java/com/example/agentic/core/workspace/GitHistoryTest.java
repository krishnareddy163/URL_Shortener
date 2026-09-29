package com.example.agentic.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitHistoryTest {
    @TempDir
    Path root;

    @Test
    void commitsOnlyTheGivenPathsWithAuthorAndTrailers() throws Exception {
        Files.writeString(root.resolve("A.java"), "class A {}");
        Files.writeString(root.resolve("Other.java"), "class Other {}");
        GitHistory history = new GitHistory(root);

        String commit = history.commit(List.of("A.java"), "implement: add A", "Why A.\n\nApproved-by: alice", "developer").orElseThrow();

        assertThat(commit).matches("[0-9a-f]{40}");
        assertThat(git("log", "--format=%an <%ae>|%cn|%s|%b")).contains("developer <developer@agents.local>|agentic-sdlc engine|implement: add A|Why A.")
                .contains("Approved-by: alice");
        assertThat(git("show", "--name-only", "--format=", "HEAD").strip()).isEqualTo("A.java");
        assertThat(git("status", "--porcelain")).contains("?? Other.java");
    }

    @Test
    void anUnchangedPathMakesNoEmptyCommit() throws Exception {
        Files.writeString(root.resolve("A.java"), "class A {}");
        GitHistory history = new GitHistory(root);
        history.commit(List.of("A.java"), "first", "body", "developer").orElseThrow();

        assertThat(history.commit(List.of("A.java"), "again", "body", "developer")).isEmpty();
        assertThat(history.commit(List.of(), "nothing", "body", "developer")).isEmpty();
        assertThat(git("rev-list", "--count", "HEAD").strip()).isEqualTo("1");
    }

    @Test
    void aMissingGitSwitchesHistoryOffWithoutFailing() throws Exception {
        Files.writeString(root.resolve("A.java"), "class A {}");
        GitHistory history = new GitHistory(root, "definitely-not-a-git-executable");

        assertThat(history.commit(List.of("A.java"), "s", "b", "developer")).isEmpty();
        assertThat(history.commit(List.of("A.java"), "s", "b", "developer")).as("stays off").isEmpty();
        assertThat(root.resolve(".git")).doesNotExist();
    }

    private String git(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero();
        return output;
    }
}
