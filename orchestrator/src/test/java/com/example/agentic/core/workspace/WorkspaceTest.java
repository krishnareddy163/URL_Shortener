package com.example.agentic.core.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceTest {
    private static final List<String> ALL = List.of("**");

    @TempDir
    Path dir;

    private Workspace workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = new Workspace(dir.resolve("workspace"), dir.resolve("staging"));
        Files.createDirectories(workspace.root().resolve("src"));
        Files.writeString(workspace.root().resolve("src/A.java"), "class A {}\n");
        Files.createDirectories(workspace.root().resolve("target"));
        Files.writeString(workspace.root().resolve("target/ignored.class"), "binary");
    }

    @Test
    void discardedStagingLeavesWorkspaceByteIdentical() throws Exception {
        String before = workspace.treeHash();

        Staging staging = workspace.stage("node");
        staging.apply(Map.of("src/A.java", "class A { broken }\n", "src/B.java", "class B {}\n"), new PathGuard(), ALL);
        staging.discard();

        assertThat(workspace.treeHash()).isEqualTo(before);
        assertThat(Files.readString(workspace.root().resolve("src/A.java"))).isEqualTo("class A {}\n");
        assertThat(Files.exists(staging.path())).isFalse();
    }

    @Test
    void stagingExcludesBuildOutput() throws Exception {
        Staging staging = workspace.stage("node");
        assertThat(Files.exists(staging.path().resolve("target"))).isFalse();
        assertThat(Files.exists(staging.path().resolve("src/A.java"))).isTrue();
    }

    @Test
    void applyIsAllOrNothingWhenAnyPathIsInvalid() throws Exception {
        Staging staging = workspace.stage("node");
        assertThatThrownBy(() -> staging.apply(Map.of("src/ok.java", "ok", "../escape.txt", "x"), new PathGuard(), ALL))
                .isInstanceOf(PathViolationException.class);
        assertThat(Files.exists(staging.path().resolve("src/ok.java"))).isFalse();
    }

    @Test
    void promoteCopiesOnlyProposedFilesAndRevertRestoresPreImages() throws Exception {
        String original = workspace.treeHash();
        Staging staging = workspace.stage("node");
        staging.apply(Map.of("src/A.java", "class A { v2 }\n", "src/B.java", "class B {}\n"), new PathGuard(), ALL);
        Files.writeString(staging.path().resolve("src/Unproposed.java"), "not promoted");

        workspace.promote(staging.path(), staging.baseHashes(), dir.resolve("promotions/node/h1"));

        assertThat(Files.readString(workspace.root().resolve("src/A.java"))).isEqualTo("class A { v2 }\n");
        assertThat(Files.exists(workspace.root().resolve("src/B.java"))).isTrue();
        assertThat(Files.exists(workspace.root().resolve("src/Unproposed.java"))).isFalse();

        Path record = dir.resolve("promotions/node/h1");
        List<String> reverted = workspace.revertAll(List.of(record)).get(record);

        assertThat(reverted).containsExactlyInAnyOrder("src/A.java", "src/B.java");
        assertThat(workspace.treeHash()).isEqualTo(original);
    }

    @Test
    void promotionFailsIfTheWorkspaceChangedSinceStaging() throws Exception {
        Staging staging = workspace.stage("node");
        staging.apply(Map.of("src/A.java", "class A { mine }\n"), new PathGuard(), ALL);
        Files.writeString(workspace.root().resolve("src/A.java"), "class A { someone else }\n");

        assertThatThrownBy(() -> workspace.promote(staging.path(), staging.baseHashes(), dir.resolve("promotions/n/h")))
                .isInstanceOf(PromotionConflictException.class);
        assertThat(Files.readString(workspace.root().resolve("src/A.java"))).isEqualTo("class A { someone else }\n");
    }

    @Test
    void revertRefusesToClobberLaterChanges() throws Exception {
        Staging staging = workspace.stage("node");
        staging.apply(Map.of("src/A.java", "class A { v2 }\n"), new PathGuard(), ALL);
        workspace.promote(staging.path(), staging.baseHashes(), dir.resolve("promotions/node/h1"));
        Files.writeString(workspace.root().resolve("src/A.java"), "class A { v3 by another node }\n");

        assertThatThrownBy(() -> workspace.revertAll(List.of(dir.resolve("promotions/node/h1"))))
                .isInstanceOf(PromotionConflictException.class);
    }

    @Test
    void viewIsReadOnlyAndConfinedToTheWorkspace() {
        assertThat(workspace.view().listFiles()).containsExactly("src/A.java");
        assertThat(workspace.view().read("src/A.java")).contains("class A {}\n");
        assertThat(workspace.view().read("../../etc/passwd")).isEmpty();
    }

    @Test
    void revertAllIsAllOrNothingWhenAnyRecordConflicts() throws Exception {
        Staging first = workspace.stage("first");
        first.apply(Map.of("src/B.java", "class B {}\n"), new PathGuard(), ALL);
        workspace.promote(first.path(), first.baseHashes(), dir.resolve("promotions/first/h1"));
        Staging second = workspace.stage("second");
        second.apply(Map.of("src/A.java", "class A { v2 }\n"), new PathGuard(), ALL);
        workspace.promote(second.path(), second.baseHashes(), dir.resolve("promotions/second/h2"));
        Files.writeString(workspace.root().resolve("src/A.java"), "class A { edited later }\n");
        String before = workspace.treeHash();

        assertThatThrownBy(() -> workspace.revertAll(List.of(dir.resolve("promotions/second/h2"),
                dir.resolve("promotions/first/h1")))).isInstanceOf(PromotionConflictException.class);

        assertThat(workspace.treeHash()).as("nothing restored when any record conflicts").isEqualTo(before);
        assertThat(Files.exists(workspace.root().resolve("src/B.java"))).isTrue();
    }

    @Test
    void viewDoesNotFollowSymlinkedDirectoriesOutOfTheWorkspace() throws Exception {
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(workspace.root().resolve("link"), outside);

        assertThat(workspace.view().read("link/secret.txt")).isEmpty();
        assertThat(workspace.view().read("src")).as("directories are not readable as files").isEmpty();
    }
}
