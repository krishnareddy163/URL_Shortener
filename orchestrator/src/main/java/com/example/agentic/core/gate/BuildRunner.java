package com.example.agentic.core.gate;

import com.example.agentic.core.policy.PolicyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs the build tool against untrusted, generated code (R10): inside the staging directory, with a hard
 * timeout that kills the whole process tree, a bounded {@code MAVEN_OPTS}, and an environment stripped to
 * an allowlist so secrets such as {@code ANTHROPIC_API_KEY} never reach the child.
 *
 * <p>With a sandbox ({@link PolicyConfig.Sandbox}) the build runs in a container instead: no network, read-only
 * root filesystem, all capabilities dropped, no privilege escalation, an unprivileged user, memory/CPU/process
 * limits, and the host's Maven cache mounted read-only (Maven's read-only "tail" repository). The staging tree is
 * streamed in as a tar archive on stdin and built in a private tmpfs copy, so the build cannot write anything on
 * the host, including the staged files that will be promoted. On timeout the container is removed as well.
 */
public final class BuildRunner {
    private static final Logger log = LoggerFactory.getLogger(BuildRunner.class);
    private static final Pattern SAFE_COMMAND = Pattern.compile("[A-Za-z0-9._/-]+");
    private static final Pattern SAFE_IMAGE = Pattern.compile("[A-Za-z0-9._/:@-]+");
    private static final Pattern SAFE_LIMIT = Pattern.compile("\\d{1,6}(\\.\\d{1,3})?[bkmg]?");
    private static final List<String> DOCKER_ENV = List.of("PATH", "HOME", "DOCKER_HOST", "DOCKER_CONTEXT",
            "DOCKER_CONFIG", "DOCKER_CERT_PATH", "DOCKER_TLS_VERIFY");
    private static final String REPOSITORY_MOUNT = "/m2-ro";
    private static final int DOCKER_DAEMON_ERROR = 125;
    private static final int MOUNT_RETRIES = 5;
    private static final long MOUNT_BACKOFF_MILLIS = 200;

    private final PolicyConfig.Build settings;
    private final PolicyConfig.Sandbox sandbox;
    private final boolean insideContainer;
    private static final String DOCKER = "docker";
    /** Set by this project's Dockerfile: the engine itself already runs in a container. */
    private static final String IN_CONTAINER = "AGENTIC_SDLC_CONTAINER";

    private boolean probed;
    private String sandboxProblem;
    private String dockerExecutable = DOCKER;
    private String tarExecutable = "tar";

    public BuildRunner(PolicyConfig.Build settings) {
        this(settings, System.getenv(IN_CONTAINER) != null);
    }

    /** @param insideContainer whether the engine itself runs in this project's container (tests pin it) */
    BuildRunner(PolicyConfig.Build settings, boolean insideContainer) {
        this.insideContainer = insideContainer;
        if (settings.command() == null || !SAFE_COMMAND.matcher(settings.command()).matches()) {
            throw new IllegalArgumentException("build command must be a plain executable name or path: " + settings.command());
        }
        this.settings = settings;
        this.sandbox = settings.sandbox();
        if (sandbox.mode() != PolicyConfig.SandboxMode.NONE) {
            requireMatch(SAFE_IMAGE, sandbox.image(), "sandbox image");
            requireMatch(SAFE_LIMIT, sandbox.memory(), "sandbox memory");
            requireMatch(SAFE_LIMIT, sandbox.cpus(), "sandbox cpus");
        }
    }

    /**
     * Result of one build invocation.
     *
     * @param exitCode process exit code ({@code -1} on timeout or start failure)
     * @param timedOut whether the timeout fired
     * @param output   combined stdout and stderr
     */
    public record BuildResult(int exitCode, boolean timedOut, String output) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    /** JaCoCo's CSV summary; the only file a build may hand back (see {@link #run(Path, List, Path, boolean)}). */
    public static final String COVERAGE_REPORT = "target/site/jacoco/jacoco.csv";
    /** Line that precedes {@link #COVERAGE_REPORT} in the build output. */
    public static final String REPORT_MARKER = "==agentic-sdlc coverage report==";

    public BuildResult run(Path workingDirectory, List<String> arguments, Path logFile) {
        return run(workingDirectory, arguments, logFile, false);
    }

    /**
     * Runs a build. With {@code emitCoverage}, the fixed file {@link #COVERAGE_REPORT} is appended to the output
     * after {@link #REPORT_MARKER} when the build produced it: in the sandbox nothing else leaves the container, and
     * the path is a constant, never an input.
     */
    public BuildResult run(Path workingDirectory, List<String> arguments, Path logFile, boolean emitCoverage) {
        boolean contained = sandboxed();
        if (!contained && sandbox.mode() == PolicyConfig.SandboxMode.DOCKER) {
            return new BuildResult(-1, false, "build sandbox required by policy but unavailable: "
                    + sandboxProblem().orElse("unknown"));
        }
        if (!contained) {
            return withHostReport(runOnce(false, workingDirectory, arguments, logFile, false), workingDirectory, emitCoverage);
        }
        BuildResult result = runOnce(true, workingDirectory, arguments, logFile, emitCoverage);
        for (int retry = 1; retry <= MOUNT_RETRIES && mountNotVisibleYet(result); retry++) {
            // Docker Desktop's file sharing can lag behind a directory the host just created: the daemon rejects
            // the bind mount (exit 125) before the build starts. That is infrastructure, not the proposal: retry.
            try {
                Thread.sleep(MOUNT_BACKOFF_MILLIS * retry);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return result;
            }
            result = runOnce(true, workingDirectory, arguments, logFile, emitCoverage);
        }
        if (result.exitCode() == DOCKER_DAEMON_ERROR) {
            return new BuildResult(result.exitCode(), false,
                    "build sandbox error (docker exit 125, the build did not start):\n" + result.output());
        }
        return result;
    }

    private static BuildResult withHostReport(BuildResult result, Path workingDirectory, boolean emitCoverage) {
        Path report = workingDirectory.resolve(COVERAGE_REPORT);
        if (!emitCoverage || !Files.isRegularFile(report)) {
            return result;
        }
        try {
            return new BuildResult(result.exitCode(), result.timedOut(),
                    result.output() + "\n" + REPORT_MARKER + "\n" + Files.readString(report));
        } catch (IOException exception) {
            return result;
        }
    }

    static boolean mountNotVisibleYet(BuildResult result) {
        return result.exitCode() == DOCKER_DAEMON_ERROR && result.output().contains("bind source path does not exist");
    }

    private BuildResult runOnce(boolean contained, Path workingDirectory, List<String> arguments, Path logFile,
                                boolean emitCoverage) {
        String container = "agentic-gate-" + UUID.randomUUID();
        List<String> command = contained ? dockerCommand(container, arguments, emitCoverage) : hostCommand(arguments);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        restrictEnvironment(builder.environment(), contained ? DOCKER_ENV : settings.passEnv());
        if (!contained) {
            builder.environment().put("MAVEN_OPTS", settings.mavenOpts());
        }
        try {
            Files.createDirectories(Objects.requireNonNull(logFile.toAbsolutePath().getParent()));
            builder.redirectOutput(logFile.toFile());
            if (log.isDebugEnabled()) {
                log.debug("running {} in {}", LogSafe.clean(command), LogSafe.clean(workingDirectory));
            }
            List<Process> processes = contained ? ProcessBuilder.startPipeline(List.of(archive(workingDirectory), builder))
                    : List.of(builder.start());
            Process process = processes.getLast();
            if (!process.waitFor(settings.timeoutSeconds(), TimeUnit.SECONDS)) {
                if (log.isWarnEnabled()) {
                    log.warn("build timed out after {} s in {}; killing process tree", settings.timeoutSeconds(),
                            LogSafe.clean(workingDirectory));
                }
                for (Process started : processes) {
                    started.descendants().forEach(ProcessHandle::destroyForcibly);
                    started.destroyForcibly();
                }
                process.waitFor(5, TimeUnit.SECONDS);
                if (contained) {
                    removeContainer(container);
                }
                return new BuildResult(-1, true, readQuietly(logFile));
            }
            return new BuildResult(process.exitValue(), false, readQuietly(logFile));
        } catch (IOException exception) {
            return new BuildResult(-1, false, "cannot start build tool '" + command.getFirst() + "': "
                    + exception.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new BuildResult(-1, false, "build interrupted");
        }
    }

    /** {@code tar} of the staging tree to stdout; the container unpacks it into its private /work. */
    private ProcessBuilder archive(Path workingDirectory) {
        ProcessBuilder archive = new ProcessBuilder(List.of(tarExecutable, "-c", "-f", "-", "-C",
                workingDirectory.toAbsolutePath().toString(), ".")).redirectError(ProcessBuilder.Redirect.DISCARD);
        restrictEnvironment(archive.environment(), List.of("PATH"));
        // macOS tar would otherwise add AppleDouble ("._*") entries for extended attributes.
        archive.environment().put("COPYFILE_DISABLE", "1");
        return archive;
    }

    public int timeoutSeconds() {
        return settings.timeoutSeconds();
    }

    /** Whether builds run in the container: always for DOCKER mode's intent, for AUTO only if it is usable. */
    public boolean sandboxed() {
        return sandbox.mode() != PolicyConfig.SandboxMode.NONE && sandboxProblem().isEmpty();
    }

    /** One line for the console: where gate builds run. */
    public String describe() {
        if (sandboxed()) {
            return "build gates run in a network-less container (" + sandbox.image() + ")";
        }
        return switch (sandbox.mode()) {
            case NONE -> "build gates run on the host (sandbox disabled in policies.yaml)";
            case AUTO -> insideContainer
                    ? "build gates run directly inside this container (no nested sandbox)"
                    : "build gates run on the host (container sandbox unavailable: " + sandboxProblem().orElse("") + ")";
            case DOCKER -> "build gates will FAIL: container sandbox required but unavailable: " + sandboxProblem().orElse("");
        };
    }

    List<String> dockerCommand(String container, List<String> arguments) {
        return dockerCommand(container, arguments, false);
    }

    List<String> dockerCommand(String container, List<String> arguments, boolean emitCoverage) {
        String build = emitCoverage
                ? "{ \"$@\"; code=$?; if [ -f " + COVERAGE_REPORT + " ]; then echo '" + REPORT_MARKER + "'; cat "
                        + COVERAGE_REPORT + "; fi; exit $code; }"
                : "exec \"$@\"";
        List<String> command = new ArrayList<>(List.of(dockerExecutable, "run", "--rm", "-i", "--name", container,
                "--network", "none", "--read-only", "--user", "65534:65534",
                "--tmpfs", "/tmp:rw,exec,size=1g,mode=1777", "--tmpfs", "/work:rw,exec,size=2g,mode=1777",
                "--security-opt", "no-new-privileges", "--cap-drop", "ALL",
                "--memory", sandbox.memory(), "--cpus", sandbox.cpus(), "--pids-limit", String.valueOf(sandbox.pidsLimit()),
                "-e", "HOME=/tmp", "-e", "MAVEN_OPTS=" + settings.mavenOpts(),
                "--mount", "type=bind,src=" + hostRepository() + ",dst=" + REPOSITORY_MOUNT + ",readonly",
                "-w", "/work", "--entrypoint", "sh", sandbox.image(),
                "-c", "tar -x -f - -C /work --no-same-owner --no-same-permissions --no-overwrite-dir"
                        + " --warning=no-unknown-keyword --warning=no-timestamp && " + build,
                "build", settings.command()));
        command.addAll(arguments);
        command.addAll(List.of("-o", "-Dmaven.repo.local=/tmp/m2", "-Dmaven.repo.local.tail=" + REPOSITORY_MOUNT));
        return command;
    }

    private List<String> hostCommand(List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(settings.command());
        command.addAll(arguments);
        return command;
    }

    /** Why the sandbox cannot be used, or empty if it can; checked once per runner. */
    private synchronized Optional<String> sandboxProblem() {
        if (!probed) {
            sandboxProblem = probeSandbox().orElse(null);
            probed = true;
        }
        return Optional.ofNullable(sandboxProblem);
    }

    private Optional<String> probeSandbox() {
        if (sandbox.mode() == PolicyConfig.SandboxMode.NONE) {
            return Optional.of("disabled");
        }
        Path repository = hostRepository();
        if (!Files.isDirectory(repository)) {
            return Optional.of("no Maven repository at " + repository + " (run make build first)");
        }
        if (repository.toString().contains(",") || repository.toString().contains("=")) {
            return Optional.of("Maven repository path cannot be mounted: " + repository);
        }
        Optional<Path> dockerPath = onPath(DOCKER);
        Optional<Path> tarPath = onPath("tar");
        if (dockerPath.isEmpty() || tarPath.isEmpty()) {
            return Optional.of(dockerPath.isEmpty() ? "docker is not installed" : "tar is not installed");
        }
        // Resolve both executables once and run them by absolute path from here on.
        dockerExecutable = dockerPath.get().toString();
        tarExecutable = tarPath.get().toString();
        try {
            ProcessBuilder probe = new ProcessBuilder(List.of(dockerExecutable, "image", "inspect", sandbox.image()))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            restrictEnvironment(probe.environment(), DOCKER_ENV);
            Process process = probe.start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.of("docker did not answer");
            }
            return process.exitValue() == 0 ? Optional.empty()
                    : Optional.of("image " + sandbox.image() + " is not available locally (docker pull " + sandbox.image() + ")");
        } catch (IOException _) {
            return Optional.of("docker is not installed");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.of("interrupted while checking docker");
        }
    }

    /** The first executable named {@code name} on this process's PATH. */
    private static Optional<Path> onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        for (String directory : path.split(File.pathSeparator)) {
            if (!directory.isBlank()) {
                Path candidate = Path.of(directory).resolve(name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath().normalize());
                }
            }
        }
        return Optional.empty();
    }

    private Path hostRepository() {
        return sandbox.hostRepository().isBlank()
                ? Path.of(System.getProperty("user.home"), ".m2", "repository")
                : Path.of(sandbox.hostRepository()).toAbsolutePath().normalize();
    }

    private void removeContainer(String container) {
        try {
            ProcessBuilder remove = new ProcessBuilder(List.of(dockerExecutable, "rm", "-f", container))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            restrictEnvironment(remove.environment(), DOCKER_ENV);
            remove.start().waitFor(20, TimeUnit.SECONDS);
        } catch (IOException exception) {
            log.warn("could not remove timed-out build container {}: {}", LogSafe.clean(container),
                    LogSafe.clean(exception.getMessage()));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void restrictEnvironment(Map<String, String> environment, List<String> allowed) {
        Map<String, String> inherited = System.getenv();
        environment.clear();
        for (String name : allowed) {
            String value = inherited.get(name);
            if (value != null) {
                environment.put(name, value);
            }
        }
    }

    private static void requireMatch(Pattern pattern, String value, String what) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " has an unsafe value: " + value);
        }
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException _) {
            return "";
        }
    }
}
