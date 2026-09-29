package com.example.agentic.core.policy;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.workspace.Diff;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Positive and negative cases for every policy gate, using the real policies/policies.yaml. */
class PolicyGatesTest {
    private static PolicyConfig policy;

    @BeforeAll
    static void loadPolicy() throws Exception {
        policy = PolicyConfig.load(Path.of(System.getProperty("repo.root", "..")).resolve("policies/policies.yaml"));
    }

    @Test
    void secretScanFlagsCredentialsInAddedLinesOnly() {
        SecretScanGate gate = new SecretScanGate(policy);
        assertThat(gate.evaluate(added("src/main/resources/application.properties", "aws.key=AKIAABCDEFGHIJKLMNOP")))
                .isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(added("key.pem", "-----BEGIN RSA PRIVATE KEY-----"))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(added("app.properties", "db.password=hunter2"))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(added("Client.java", "String api_key = \"sk_live_abcdefgh\";"))).isInstanceOf(GateResult.Fail.class);

        assertThat(gate.evaluate(added("app.properties", "spring.datasource.password=\nshortener.base-url="))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(added("Readme.md", "Set the password via the environment."))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(modified("app.properties", "db.password=legacy\n", "db.password=legacy\nnew.flag=true\n")))
                .as("pre-existing lines are not re-flagged").isEqualTo(GateResult.pass());
    }

    @Test
    void secretScanNeverEchoesTheSecret() {
        GateResult result = new SecretScanGate(policy).evaluate(added("a.properties", "password=TopSecretValue"));
        assertThat(((GateResult.Fail) result).reason()).doesNotContain("TopSecretValue");
    }

    @Test
    void forbiddenApiAppliesToMainSourcesOnly() {
        ForbiddenApiGate gate = new ForbiddenApiGate(policy);
        for (String line : List.of("Runtime.getRuntime().exec(cmd);", "new ProcessBuilder(\"sh\").start();",
                "var in = new ObjectInputStream(stream);", "Class.forName(className);", "ScriptEngineManager m = null;")) {
            assertThat(gate.evaluate(added("src/main/java/A.java", line))).as(line).isInstanceOf(GateResult.Fail.class);
        }
        assertThat(gate.evaluate(added("src/main/java/A.java", "Class.forName(\"org.h2.Driver\");"))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(added("src/test/java/ATest.java", "new ProcessBuilder(\"ls\");"))).isEqualTo(GateResult.pass());
    }

    @Test
    void dependencyAllowlistRejectsOnlyNewUnlistedDependencies() {
        DependencyAllowlistGate gate = new DependencyAllowlistGate(policy);
        String base = pom("<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>");
        String withAllowed = pom("<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>"
                + "<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId></dependency>");
        String withUnlisted = pom("<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>"
                + "<dependency><groupId>com.evil</groupId><artifactId>miner</artifactId></dependency>");

        assertThat(gate.evaluate(modified("pom.xml", base, withAllowed))).isEqualTo(GateResult.pass());
        GateResult rejected = gate.evaluate(modified("pom.xml", base, withUnlisted));
        assertThat(rejected).isInstanceOf(GateResult.Fail.class);
        assertThat(((GateResult.Fail) rejected).reason()).contains("com.evil:miner");
        assertThat(gate.evaluate(added("pom.xml", "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><project/>")))
                .as("DTDs are refused").isInstanceOf(GateResult.Fail.class);
    }

    @Test
    void buildCodeAddedToThePomMustBeAllowlistedBeforeAnyBuildRuns() {
        DependencyAllowlistGate gate = new DependencyAllowlistGate(policy);
        String base = buildPom("<plugin><artifactId>maven-surefire-plugin</artifactId></plugin>", "");

        assertThat(gate.evaluate(modified("pom.xml", base, buildPom("<plugin><artifactId>maven-surefire-plugin</artifactId></plugin>"
                + "<plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId></plugin>", ""))))
                .as("allowlisted plugin").isEqualTo(GateResult.pass());
        assertThat(reason(gate, base, buildPom("<plugin><groupId>org.codehaus.mojo</groupId>"
                + "<artifactId>exec-maven-plugin</artifactId></plugin>", "")))
                .contains("build plugin or parent POM not on allowlist: org.codehaus.mojo:exec-maven-plugin");
        assertThat(reason(gate, base, buildPom("<plugin><artifactId>maven-antrun-plugin</artifactId></plugin>", "")))
                .as("a plugin without groupId defaults to org.apache.maven.plugins")
                .contains("org.apache.maven.plugins:maven-antrun-plugin");
        assertThat(reason(gate, base, buildPom("<plugin><artifactId>maven-surefire-plugin</artifactId><dependencies><dependency>"
                + "<groupId>com.evil</groupId><artifactId>provider</artifactId></dependency></dependencies></plugin>", "")))
                .as("plugin dependencies are build code").contains("com.evil:provider");
        assertThat(reason(gate, base, "<project><parent><groupId>com.evil</groupId><artifactId>parent</artifactId></parent>"
                + base.substring("<project>".length())))
                .contains("com.evil:parent");
        assertThat(reason(gate, base, buildPom("<plugin><artifactId>maven-surefire-plugin</artifactId></plugin>",
                "<repositories><repository><id>x</id><url>https://repo.evil.example</url></repository></repositories>")))
                .contains("may not add repositories or build extensions").contains("repo.evil.example");
        assertThat(reason(gate, base, buildPom("<plugin><artifactId>maven-surefire-plugin</artifactId></plugin>"
                + "</plugins><extensions><extension><groupId>com.evil</groupId><artifactId>ext</artifactId></extension>"
                + "</extensions><plugins>", "")))
                .contains("may not add repositories or build extensions");

        String evilBaseline = buildPom("<plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId></plugin>", "");
        assertThat(gate.evaluate(modified("pom.xml", evilBaseline, evilBaseline.replace("</project>", "<name>x</name></project>"))))
                .as("what a POM already had is not re-flagged").isEqualTo(GateResult.pass());
    }

    @Test
    void theCommittedShortenerPomPassesTheBuildAllowlist() throws Exception {
        String pom = Files.readString(Path.of(System.getProperty("repo.root", "..")).resolve("shortener-service/pom.xml"));
        assertThat(new DependencyAllowlistGate(policy).evaluate(added("pom.xml", pom))).isEqualTo(GateResult.pass());
    }

    @Test
    void noRawIpLoggingFlagsLoggingOfTheRemoteAddress() {
        NoRawIpLoggingGate gate = new NoRawIpLoggingGate(policy);
        assertThat(gate.evaluate(added("A.java", "log.info(\"client {}\", request.getRemoteAddr());"))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(added("A.java", "LOGGER.warn(\"from \" + remoteAddr);"))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(added("A.java", "String key = hash(request.getRemoteAddr());"))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(added("A.java", "log.info(\"client key {}\", clientKey);"))).isEqualTo(GateResult.pass());
    }

    @Test
    void pathAllowlistRequiresADeclaredScopeAndMatchingPaths() {
        PathAllowlistGate gate = new PathAllowlistGate(policy);
        Node node = new Node("n", "developer", Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null);
        assertThat(gate.evaluate(new GateContext(node, "developer", null, null, Diff.empty(), Map.of(), Path.of("."), Path.of("."))))
                .isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(new GateContext(node, "stranger", null, null, Diff.empty(), Map.of(), Path.of("."), Path.of("."))))
                .isInstanceOf(GateResult.Fail.class);
        Proposal outside = new Proposal(Map.of("src/test/java/T.java", "x"), "r", List.of("requirement"), Map.of());
        assertThat(gate.evaluate(new GateContext(node, "developer", outside, Path.of("."), Diff.empty(), Map.of(), Path.of("."), Path.of("."))))
                .isInstanceOf(GateResult.Fail.class);
    }

    @Test
    void riskRulesHitAndMiss() {
        assertThat(new MigrationPathRule(List.of("**/db/migration/**")).flag(added("src/main/resources/db/migration/V2__x.sql", "x").diff())).isPresent();
        assertThat(new MigrationPathRule(List.of("**/db/migration/**")).flag(added("src/main/java/A.java", "x").diff())).isEmpty();
        assertThat(new PomChangeRule().flag(added("pom.xml", "<project/>").diff())).isPresent();
        assertThat(new PomChangeRule().flag(added("docs/pom-notes.md", "x").diff())).isEmpty();
        assertThat(new DiffSizeRule(3).flag(added("A.java", "1\n2\n3\n4\n").diff())).isPresent();
        assertThat(new DiffSizeRule(3).flag(added("A.java", "1\n2\n3\n").diff())).isEmpty();
        Map<String, String> after = new HashMap<>();
        after.put("old.txt", null);
        assertThat(new DeletionRule().flag(Diff.of(Map.of("old.txt", "x"), after))).isPresent();
        assertThat(new DeletionRule().flag(added("new.txt", "x").diff())).isEmpty();
    }

    private static GateContext added(String path, String content) {
        return modified(path, null, content);
    }

    private static GateContext modified(String path, String before, String after) {
        Map<String, String> old = new HashMap<>();
        old.put(path, before);
        Node node = new Node("n", "developer", Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null);
        Proposal proposal = new Proposal(Map.of(path, after), "r", List.of("requirement"), Map.of());
        return new GateContext(node, "developer", proposal, Path.of("."), Diff.of(old, Map.of(path, after)), Map.of(),
                Path.of("."), Path.of("."));
    }

    private static String reason(DependencyAllowlistGate gate, String before, String after) {
        GateResult result = gate.evaluate(modified("pom.xml", before, after));
        assertThat(result).isInstanceOf(GateResult.Fail.class);
        return ((GateResult.Fail) result).reason();
    }

    private static String buildPom(String plugins, String extra) {
        return "<project>" + extra + "<build><plugins>" + plugins + "</plugins></build></project>";
    }

    private static String pom(String dependencies) {
        return "<project><dependencies>" + dependencies + "</dependencies><build><plugins><plugin>"
                + "<dependencies><dependency><groupId>x</groupId><artifactId>plugin-only</artifactId></dependency></dependencies>"
                + "</plugin></plugins></build></project>";
    }
}
