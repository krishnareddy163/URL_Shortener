package com.example.agentic.core.policy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Governance configuration loaded from {@code policies/policies.yaml}: default budgets, per-agent path
 * scopes, dependency and build-plugin allowlists, scanner patterns, risk thresholds and build sandbox settings.
 *
 * @param budgets             default run budgets (a workflow may override)
 * @param pathScopes          agent id to the globs it may write; an agent absent here may not run
 * @param dependencyAllowlist {@code groupId:artifactId} entries a pom.xml may add
 * @param buildAllowlist      {@code groupId:artifactId} build plugins, plugin dependencies and parent POMs a pom.xml may add
 * @param secretPatterns      regexes applied to added lines by {@code secret-scan}
 * @param forbiddenApiPatterns regexes applied to added {@code src/main/**} lines by {@code forbidden-api}
 * @param rawIpTokens         tokens that must not appear on logging lines ({@code no-raw-ip-logging})
 * @param risk                risk-rule settings
 * @param build               build tool sandbox settings
 * @param coverage            test coverage target, floor and documented exceptions ({@code test-coverage})
 */
public record PolicyConfig(
        Map<String, Integer> budgets,
        Map<String, List<String>> pathScopes,
        List<String> dependencyAllowlist,
        List<String> buildAllowlist,
        List<String> secretPatterns,
        List<String> forbiddenApiPatterns,
        List<String> rawIpTokens,
        Risk risk,
        Build build,
        Coverage coverage) {

    public PolicyConfig {
        budgets = budgets == null ? Map.of() : Map.copyOf(budgets);
        pathScopes = pathScopes == null ? Map.of() : Map.copyOf(pathScopes);
        dependencyAllowlist = dependencyAllowlist == null ? List.of() : List.copyOf(dependencyAllowlist);
        buildAllowlist = buildAllowlist == null ? List.of() : List.copyOf(buildAllowlist);
        secretPatterns = secretPatterns == null ? List.of() : List.copyOf(secretPatterns);
        forbiddenApiPatterns = forbiddenApiPatterns == null ? List.of() : List.copyOf(forbiddenApiPatterns);
        rawIpTokens = rawIpTokens == null ? List.of() : List.copyOf(rawIpTokens);
        risk = risk == null ? new Risk(200, List.of("**/db/migration/**")) : risk;
        build = build == null ? new Build("mvn", 120, "-Xmx768m", List.of("PATH", "JAVA_HOME", "HOME"), null) : build;
        coverage = coverage == null ? Coverage.strict() : coverage;
    }

    /**
     * Test coverage policy for the {@code test-coverage} gate. Every class below {@code target} is reported; the
     * gate fails when overall coverage drops below the minimums or when a class other than a documented exception
     * misses the target.
     *
     * @param target        the goal for line and branch coverage, in percent
     * @param minimumLine   overall line coverage below which the gate fails, in percent
     * @param minimumBranch overall branch coverage below which the gate fails, in percent
     * @param exceptions    fully qualified class name to the reason it cannot reach the target
     */
    public record Coverage(double target, double minimumLine, double minimumBranch, Map<String, String> exceptions) {
        public Coverage {
            exceptions = exceptions == null ? Map.of() : Map.copyOf(exceptions);
        }

        /** 100% everywhere, no exceptions. */
        public static Coverage strict() {
            return new Coverage(100, 100, 100, Map.of());
        }
    }

    /**
     * Risk-rule settings.
     *
     * @param diffSizeThreshold changed lines above which {@code DiffSizeRule} escalates
     * @param migrationGlobs    globs identifying schema migrations
     */
    public record Risk(int diffSizeThreshold, List<String> migrationGlobs) {

        public Risk {
            migrationGlobs = migrationGlobs == null ? List.of() : List.copyOf(migrationGlobs);
        }
    }

    /**
     * Build sandbox settings for gates that run real tools.
     *
     * @param command        build executable (for example {@code mvn})
     * @param timeoutSeconds hard timeout; the process tree is killed on expiry
     * @param mavenOpts      value of {@code MAVEN_OPTS} for the child process
     * @param passEnv        the only environment variables passed through to the child
     * @param sandbox        container isolation for the build (defaults to {@link Sandbox#none()})
     */
    public record Build(String command, int timeoutSeconds, String mavenOpts, List<String> passEnv, Sandbox sandbox) {

        public Build {
            passEnv = passEnv == null ? List.of() : List.copyOf(passEnv);
            sandbox = sandbox == null ? Sandbox.none() : sandbox;
        }
    }

    /** Where gate builds run. */
    public enum SandboxMode {
        /** Directly on the host, with the timeout and stripped environment only. */
        NONE,
        /** Always in a network-less container; if the sandbox is unavailable the gate fails (fail closed). */
        DOCKER,
        /** In the container when Docker and the image are available locally, otherwise on the host. */
        AUTO
    }

    /**
     * Container isolation for gate builds: no network, a read-only root, all capabilities dropped, the host Maven
     * cache mounted read-only, and only the staging directory writable.
     *
     * @param mode            {@link SandboxMode}
     * @param image           container image with the build tool (never pulled by a gate; pull it once beforehand)
     * @param memory          memory limit, for example {@code 2g} (Maven plus a forked test JVM need about 1.5 GB)
     * @param cpus            CPU limit, for example {@code 2}
     * @param pidsLimit       maximum processes inside the container
     * @param hostRepository  host Maven repository to mount read-only ({@code ~/.m2/repository} when empty)
     */
    public record Sandbox(SandboxMode mode, String image, String memory, String cpus, int pidsLimit, String hostRepository) {

        public Sandbox {
            mode = mode == null ? SandboxMode.NONE : mode;
            memory = memory == null ? "2g" : memory;
            cpus = cpus == null ? "2" : cpus;
            pidsLimit = pidsLimit <= 0 ? 512 : pidsLimit;
            hostRepository = hostRepository == null ? "" : hostRepository;
        }

        public static Sandbox none() {
            return new Sandbox(SandboxMode.NONE, null, null, null, 0, null);
        }
    }

    public static PolicyConfig load(Path file) throws IOException {
        return new ObjectMapper(new YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(file.toFile(), PolicyConfig.class);
    }

    /** Globs the agent may write, or empty if the agent has no declared scope. */
    public Optional<List<String>> scopeFor(String agentId) {
        return Optional.ofNullable(pathScopes.get(agentId));
    }
}
