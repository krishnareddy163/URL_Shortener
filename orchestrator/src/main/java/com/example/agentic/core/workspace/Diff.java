package com.example.agentic.core.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Textual change set between a node's staging base and its proposal. Risk rules, secret scanning and the
 * approval view all read from this one object.
 *
 * @param changes changed files, sorted by path
 */
public record Diff(List<FileChange> changes) {
    private static final int CONTEXT = 3;
    private static final int MAX_LCS_CELLS = 4_000_000;

    public Diff {
        changes = List.copyOf(changes);
    }

    /** Kind of file change. */
    public enum ChangeType { ADDED, MODIFIED, DELETED }

    /**
     * One changed file.
     *
     * @param path   workspace-relative path
     * @param type   kind of change
     * @param before previous content, or {@code null} if added
     * @param after  new content, or {@code null} if deleted
     */
    public record FileChange(String path, ChangeType type, String before, String after) {

        public List<String> addedLines() {
            return edits().stream().filter(edit -> edit.op() == '+').map(Edit::line).toList();
        }

        public int changedLines() {
            return (int) edits().stream().filter(edit -> edit.op() != ' ').count();
        }

        /** Unified diff with three lines of context. */
        public String unified() {
            List<Edit> edits = edits();
            StringBuilder out = new StringBuilder()
                    .append("--- ").append(before == null ? "/dev/null" : "a/" + path).append('\n')
                    .append("+++ ").append(after == null ? "/dev/null" : "b/" + path).append('\n');
            int firstChange = -1;
            int lastChange = -1;
            for (int index = 0; index < edits.size(); index++) {
                if (edits.get(index).op() == ' ') {
                    continue;
                }
                if (firstChange >= 0 && index - lastChange > 2 * CONTEXT) {
                    appendHunk(out, edits, firstChange, lastChange);
                    firstChange = -1;
                }
                if (firstChange < 0) {
                    firstChange = index;
                }
                lastChange = index;
            }
            if (firstChange >= 0) {
                appendHunk(out, edits, firstChange, lastChange);
            }
            return out.toString();
        }

        private static void appendHunk(StringBuilder out, List<Edit> edits, int firstChange, int lastChange) {
            int start = Math.max(0, firstChange - CONTEXT);
            int end = Math.min(edits.size(), lastChange + CONTEXT + 1);
            int oldStart = 1;
            int newStart = 1;
            for (int index = 0; index < start; index++) {
                if (edits.get(index).op() != '+') {
                    oldStart++;
                }
                if (edits.get(index).op() != '-') {
                    newStart++;
                }
            }
            int oldCount = 0;
            int newCount = 0;
            for (int index = start; index < end; index++) {
                if (edits.get(index).op() != '+') {
                    oldCount++;
                }
                if (edits.get(index).op() != '-') {
                    newCount++;
                }
            }
            out.append("@@ -").append(oldCount == 0 ? oldStart - 1 : oldStart).append(',').append(oldCount)
                    .append(" +").append(newCount == 0 ? newStart - 1 : newStart).append(',').append(newCount)
                    .append(" @@\n");
            for (int index = start; index < end; index++) {
                out.append(edits.get(index).op()).append(edits.get(index).line()).append('\n');
            }
        }

        private static List<String> lines(String content) {
            if (content == null || content.isEmpty()) {
                return List.of();
            }
            String[] parts = content.split("\n", -1);
            int count = content.endsWith("\n") ? parts.length - 1 : parts.length;
            return List.of(parts).subList(0, count);
        }

        /** Longest-common-subsequence line diff; falls back to replace-all for very large inputs. */
        private static List<Edit> lineEdits(List<String> old, List<String> updated) {
            int n = old.size();
            int m = updated.size();
            List<Edit> edits = new ArrayList<>();
            if ((long) n * m > MAX_LCS_CELLS) {
                old.forEach(line -> edits.add(new Edit('-', line)));
                updated.forEach(line -> edits.add(new Edit('+', line)));
                return edits;
            }
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = old.get(i).equals(updated.get(j)) ? lcs[i + 1][j + 1] + 1
                            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < n && j < m) {
                if (old.get(i).equals(updated.get(j))) {
                    edits.add(new Edit(' ', old.get(i)));
                    i++;
                    j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    edits.add(new Edit('-', old.get(i++)));
                } else {
                    edits.add(new Edit('+', updated.get(j++)));
                }
            }
            while (i < n) {
                edits.add(new Edit('-', old.get(i++)));
            }
            while (j < m) {
                edits.add(new Edit('+', updated.get(j++)));
            }
            return edits;
        }

        private List<Edit> edits() {
            return lineEdits(lines(before), lines(after));
        }
    }

    /**
     * Builds a diff from base contents ({@code null} = absent) and new contents ({@code null} = deleted).
     * Unchanged files are omitted.
     */
    public static Diff of(Map<String, String> before, Map<String, String> after) {
        TreeSet<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        List<FileChange> changes = new ArrayList<>();
        for (String path : paths) {
            String old = before.get(path);
            String updated = after.containsKey(path) ? after.get(path) : old;
            if (old == null && updated != null) {
                changes.add(new FileChange(path, ChangeType.ADDED, null, updated));
            } else if (old != null && updated == null) {
                changes.add(new FileChange(path, ChangeType.DELETED, old, null));
            } else if (old != null && !old.equals(updated)) {
                changes.add(new FileChange(path, ChangeType.MODIFIED, old, updated));
            }
        }
        return new Diff(changes);
    }

    public static Diff empty() {
        return new Diff(List.of());
    }

    public int changedLines() {
        return changes.stream().mapToInt(FileChange::changedLines).sum();
    }

    public List<String> paths() {
        return changes.stream().map(FileChange::path).toList();
    }

    public String unified() {
        StringBuilder out = new StringBuilder();
        changes.forEach(change -> out.append(change.unified()));
        return out.toString();
    }

    /** One line per file: {@code A|M|D path (+added/-removed)}. */
    public String stat() {
        StringBuilder out = new StringBuilder();
        for (FileChange change : changes) {
            List<Edit> edits = change.edits();
            long added = edits.stream().filter(edit -> edit.op() == '+').count();
            long removed = edits.stream().filter(edit -> edit.op() == '-').count();
            out.append(change.type().name().charAt(0)).append(' ').append(change.path())
                    .append(" (+").append(added).append("/-").append(removed).append(")\n");
        }
        return out.toString();
    }

    private record Edit(char op, String line) {
    }

}
