package com.example.agentic.core.workspace;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DiffTest {

    @Test
    void classifiesAddedModifiedAndDeletedFiles() {
        Map<String, String> before = new HashMap<>();
        before.put("keep.txt", "same\n");
        before.put("mod.txt", "a\nb\nc\n");
        before.put("gone.txt", "x\n");
        before.put("new.txt", null);
        Map<String, String> after = new HashMap<>();
        after.put("keep.txt", "same\n");
        after.put("mod.txt", "a\nB\nc\n");
        after.put("gone.txt", null);
        after.put("new.txt", "1\n2\n");

        Diff diff = Diff.of(before, after);

        assertThat(diff.changes()).extracting(Diff.FileChange::path, Diff.FileChange::type).containsExactly(
                tuple("gone.txt", Diff.ChangeType.DELETED),
                tuple("mod.txt", Diff.ChangeType.MODIFIED),
                tuple("new.txt", Diff.ChangeType.ADDED));
        assertThat(diff.changedLines()).isEqualTo(1 + 2 + 2);
    }

    @Test
    void unifiedDiffShowsHunksWithContext() {
        String before = "l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10\n";
        String after = "l1\nl2\nl3\nl4\nCHANGED\nl6\nl7\nl8\nl9\nl10\n";
        Map<String, String> old = new HashMap<>();
        old.put("f.txt", before);

        String unified = Diff.of(old, Map.of("f.txt", after)).unified();

        assertThat(unified).contains("--- a/f.txt").contains("+++ b/f.txt").contains("@@ -2,7 +2,7 @@")
                .contains("-l5").contains("+CHANGED").contains(" l4").doesNotContain(" l1\n");
        assertThat(Diff.of(old, Map.of("f.txt", after)).changes().getFirst().addedLines()).containsExactly("CHANGED");
    }
}
