package com.example.agentic.core.workspace;

import com.example.agentic.core.gate.LogSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Git history of a run's workspace: every promotion and every invalidation revert becomes one commit, authored by
 * the agent role, with the rationale as the message and the artifact hash, run and approver as trailers. It is a
 * record for humans; the event log stays the source of truth, and each {@code NODE_DONE} names its commit.
 *
 * <p>Git runs as a fixed executable with no shell, a stripped environment, no global or system configuration (so a
 * user's hooks, signing or aliases never apply) and a timeout. Paths are passed after {@code --}. If git is missing
 * or fails, history is switched off with one warning and the run continues.
 */
public final class GitHistory {
    private static final Logger log = LoggerFactory.getLogger(GitHistory.class);
    private static final long TIMEOUT_SECONDS = 30;
    private static final String ENGINE = "agentic-sdlc engine";

    private final Path root;
    private final String git;
    private boolean disabled;

    public GitHistory(Path root) {
        this(root, "git");
    }

    GitHistory(Path root, String git) {
        this.root = root;
        this.git = git;
    }

    /**
     * Commits the given workspace paths.
     *
     * @param paths   workspace-relative paths changed by the step
     * @param subject first line of the message
     * @param body    rest of the message (rationale and trailers)
     * @param author  agent role or person the change is attributed to
     * @return the commit id, or empty when history is off or nothing changed
     */
    public synchronized Optional<String> commit(Collection<String> paths, String subject, String body, String author) {
        if (disabled || paths.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!Files.isDirectory(root.resolve(".git"))) {
                run(List.of("init", "-q", "-b", "main"), author);
            }
            List<String> add = new ArrayList<>(List.of("add", "-A", "--"));
            add.addAll(paths);
            run(add, author);
            if (run(List.of("diff", "--cached", "--quiet"), author, 0, 1) == 0) {
                return Optional.empty();
            }
            run(List.of("commit", "-q", "--no-verify", "-m", subject, "-m", body), author);
            return Optional.of(output(List.of("rev-parse", "HEAD"), author).strip());
        } catch (IOException exception) {
            disable(exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            disable("interrupted");
            return Optional.empty();
        }
    }

    private void disable(String reason) {
        disabled = true;
        log.warn("workspace git history is off for this run: {}", LogSafe.clean(reason));
    }

    private int run(List<String> arguments, String author, int... allowedExitCodes) throws IOException, InterruptedException {
        Process process = start(arguments, author, ProcessBuilder.Redirect.DISCARD);
        int exit = await(process, arguments);
        int[] allowed = allowedExitCodes.length == 0 ? new int[] {0} : allowedExitCodes;
        for (int code : allowed) {
            if (exit == code) {
                return exit;
            }
        }
        throw new IOException("git " + arguments.getFirst() + " exited with " + exit);
    }

    private String output(List<String> arguments, String author) throws IOException, InterruptedException {
        Process process = start(arguments, author, ProcessBuilder.Redirect.PIPE);
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (await(process, arguments) != 0) {
            throw new IOException("git " + arguments.getFirst() + " failed");
        }
        return text;
    }

    private Process start(List<String> arguments, String author, ProcessBuilder.Redirect output) throws IOException {
        List<String> command = new ArrayList<>(List.of(git));
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(output);
        Map<String, String> environment = builder.environment();
        String path = environment.get("PATH");
        environment.clear();
        if (path != null) {
            environment.put("PATH", path);
        }
        environment.put("HOME", root.toString());
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", "/dev/null");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("GIT_AUTHOR_NAME", author);
        environment.put("GIT_AUTHOR_EMAIL", author.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "-") + "@agents.local");
        environment.put("GIT_COMMITTER_NAME", ENGINE);
        environment.put("GIT_COMMITTER_EMAIL", "engine@agents.local");
        return builder.start();
    }

    private static int await(Process process, List<String> arguments) throws IOException, InterruptedException {
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("git " + arguments.getFirst() + " timed out");
        }
        return process.exitValue();
    }
}
