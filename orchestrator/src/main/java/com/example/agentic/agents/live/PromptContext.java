package com.example.agentic.agents.live;

import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.state.Artifact;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the bounded context sections of a live agent's prompt. Everything included is data from upstream agents
 * or the workspace and is framed as such; every section has a size cap so a large workspace cannot blow the budget.
 */
final class PromptContext {
    static final int MAX_UPSTREAM_CHARS = 60_000;
    static final int MAX_WORKSPACE_CHARS = 80_000;
    private static final int MAX_FILE_CHARS = 12_000;
    private static final String TRUNCATED = "\n...(truncated)";

    private PromptContext() {
    }

    /** Upstream artifacts: rationale, structured data and file contents, capped at {@link #MAX_UPSTREAM_CHARS}. */
    static String upstream(AgentContext context, int maxFileChars) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Artifact> entry : context.upstream().entrySet()) {
            Artifact artifact = entry.getValue();
            out.append("## Artifact from ").append(entry.getKey()).append("\nRationale: ").append(artifact.rationale())
                    .append("\nData: ").append(artifact.data()).append('\n');
            for (Map.Entry<String, String> file : artifact.files().entrySet()) {
                appendFile(out, file.getKey(), file.getValue(), maxFileChars);
                if (out.length() > MAX_UPSTREAM_CHARS) {
                    return out.substring(0, MAX_UPSTREAM_CHARS) + "\n...(upstream truncated)\n";
                }
            }
        }
        return out.toString();
    }

    /**
     * The workspace listing, then file contents: files the agent may write first (it must return them whole),
     * then the rest, until {@link #MAX_WORKSPACE_CHARS}.
     */
    static String workspace(AgentContext context, List<String> scope) {
        List<String> files = context.workspace().listFiles();
        StringBuilder out = new StringBuilder("Workspace files:\n");
        files.forEach(file -> out.append("- ").append(file).append('\n'));
        List<PathMatcher> matchers = scope.stream()
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob)).toList();
        List<String> ordered = new ArrayList<>(files.stream().filter(file -> inScope(file, matchers)).toList());
        files.stream().filter(file -> !inScope(file, matchers)).forEach(ordered::add);
        for (String file : ordered) {
            Optional<String> content = context.workspace().read(file);
            if (content.isPresent()) {
                if (out.length() + Math.min(content.get().length(), MAX_FILE_CHARS) > MAX_WORKSPACE_CHARS) {
                    out.append("...(remaining workspace files omitted)\n");
                    break;
                }
                appendFile(out, file, content.get(), MAX_FILE_CHARS);
            }
        }
        return out.toString();
    }

    /** The node's task from the human-authored workflow, which tells apart nodes that share an agent. */
    static String task(AgentContext context) {
        return context.task() == null ? ""
                : "# Your task in this workflow (node " + context.nodeId() + ")\n" + context.task()
                        + "\nDo exactly this step and nothing that belongs to a later step.\n\n";
    }

    static String feedback(AgentContext context) {
        return context.feedback() == null ? ""
                : "Your previous attempt was rejected. Fix exactly this and keep everything else working:\n<feedback>\n"
                        + context.feedback() + "\n</feedback>\n";
    }

    private static boolean inScope(String file, List<PathMatcher> matchers) {
        Path path = Path.of(file);
        return matchers.stream().anyMatch(matcher -> matcher.matches(path));
    }

    private static void appendFile(StringBuilder out, String path, String content, int maxChars) {
        out.append("### ").append(path).append("\n```\n")
                .append(content.length() > maxChars ? content.substring(0, maxChars) + TRUNCATED : content)
                .append("\n```\n");
    }
}
