package com.example.agentic.core.gate;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.workspace.Diff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Positive and negative cases for the quality and compliance gates. */
class QualityGatesTest {

    @TempDir
    Path staging;

    @Test
    void artifactMetadataRequiresRationaleAndRealLineage() {
        ArtifactMetadataGate gate = new ArtifactMetadataGate();
        Map<String, Artifact> upstream = Map.of("design", Artifact.of("design", List.of(), "d", Map.of(), Map.of()));

        assertThat(gate.evaluate(context("developer", proposal("why", List.of("design")), upstream))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(context("developer", proposal(" ", List.of("design")), upstream))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(context("developer", proposal("why", List.of()), upstream))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(context("developer", proposal("why", List.of("unrelated")), upstream))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(context("requirements", proposal("why", List.of()), Map.of())))
                .as("the requirements agent derives from the raw requirement").isEqualTo(GateResult.pass());
    }

    @Test
    void requirementsCompleteChecksStructure() {
        RequirementsCompleteGate gate = new RequirementsCompleteGate();
        Map<String, Object> complete = Map.of("problemStatement", "p", "acceptanceCriteria", List.of("a"),
                "userStories", List.of("As a visitor, I want short links, so that sharing is easy"),
                "assumptions", List.of(), "ambiguities", List.of(Map.of("id", "q-1", "question", "?", "blocking", true,
                        "options", List.of("x", "y"))));
        assertThat(gate.evaluate(context("requirements", data(complete), Map.of()))).isEqualTo(GateResult.pass());

        assertThat(gate.evaluate(context("requirements", data(Map.of("problemStatement", "p")), Map.of())))
                .isInstanceOf(GateResult.Fail.class);
        Map<String, Object> withoutStories = new HashMap<>(complete);
        withoutStories.remove("userStories");
        assertThat(gate.evaluate(context("requirements", data(withoutStories), Map.of())))
                .isEqualTo(GateResult.fail("requirements-complete", "userStories must be a non-empty list"));
        Map<String, Object> freeTextStory = new HashMap<>(complete);
        freeTextStory.put("userStories", List.of("Users can shorten links"));
        assertThat(((GateResult.Fail) gate.evaluate(context("requirements", data(freeTextStory), Map.of()))).reason())
                .contains("As a <role>, I want <capability>, so that <benefit>");
        Map<String, Object> blockingWithoutOptions = Map.of("problemStatement", "p", "acceptanceCriteria", List.of("a"),
                "userStories", List.of("As a visitor, I want short links, so that sharing is easy"),
                "assumptions", List.of(), "ambiguities", List.of(Map.of("id", "q-1", "question", "?", "blocking", true)));
        assertThat(gate.evaluate(context("requirements", data(blockingWithoutOptions), Map.of()))).isInstanceOf(GateResult.Fail.class);
    }

    @Test
    void schemaValidChecksOpenApiResponsesAndMigrationNames() throws Exception {
        SchemaValidGate gate = new SchemaValidGate();
        Files.writeString(staging.resolve("openapi.yaml"), """
                openapi: 3.0.3
                paths:
                  /x:
                    get:
                      responses:
                        '200': {description: ok}
                """);
        Path migrations = Files.createDirectories(staging.resolve("src/main/resources/db/migration"));
        Files.writeString(migrations.resolve("V1__init.sql"), "CREATE TABLE t (id INT);");
        assertThat(gate.evaluate(staged())).isEqualTo(GateResult.pass());

        Files.writeString(migrations.resolve("add_column.sql"), "ALTER TABLE t ADD c INT;");
        assertThat(gate.evaluate(staged())).isInstanceOf(GateResult.Fail.class);
        Files.delete(migrations.resolve("add_column.sql"));

        Files.writeString(migrations.resolve("V2__empty.sql"), "  ");
        assertThat(gate.evaluate(staged())).isInstanceOf(GateResult.Fail.class);
        Files.delete(migrations.resolve("V2__empty.sql"));

        Files.writeString(staging.resolve("openapi.yaml"), "openapi: 3.0.3\npaths:\n  /x:\n    get:\n      summary: no responses\n");
        GateResult noResponses = gate.evaluate(staged());
        assertThat(noResponses).isInstanceOf(GateResult.Fail.class);
        assertThat(((GateResult.Fail) noResponses).reason()).contains("GET /x has no responses");
    }

    @Test
    void impactFilesMustExistInTheRealScan() throws Exception {
        ImpactFilesExistGate gate = new ImpactFilesExistGate();
        Files.writeString(staging.resolve("openapi.yaml"), "x");
        Map<String, Object> scan = Map.of("files", List.of("src/main/java/A.java"));

        Map<String, Object> good = Map.of("scan", scan, "impact", List.of(
                Map.of("file", "src/main/java/A.java", "reason", "changes"), Map.of("file", "openapi.yaml", "reason", "contract")));
        assertThat(gate.evaluate(new GateContext(node("analyst"), "analyst", data(good), staging, Diff.empty(), Map.of(), staging, staging)))
                .isEqualTo(GateResult.pass());

        Map<String, Object> invented = Map.of("scan", scan, "impact", List.of(Map.of("file", "src/main/java/Ghost.java", "reason", "x")));
        assertThat(gate.evaluate(new GateContext(node("analyst"), "analyst", data(invented), staging, Diff.empty(), Map.of(), staging, staging)))
                .isInstanceOf(GateResult.Fail.class);
    }

    @Test
    void reviewGoRequiresAGoRecommendationUpstream() {
        ReviewGoGate gate = new ReviewGoGate();
        Artifact go = Artifact.of("review", List.of(), "r", Map.of(), Map.of("recommendation", "GO"));
        Artifact noGo = Artifact.of("review", List.of(), "r", Map.of(), Map.of("recommendation", "NO_GO"));
        assertThat(gate.evaluate(context("docs", null, Map.of("review", go)))).isEqualTo(GateResult.pass());
        assertThat(gate.evaluate(context("docs", null, Map.of("review", noGo)))).isInstanceOf(GateResult.Fail.class);
        assertThat(gate.evaluate(context("docs", null, Map.of()))).isInstanceOf(GateResult.Fail.class);
    }

    private GateContext staged() {
        return new GateContext(node("architect"), "architect", proposal("r", List.of("requirement")), staging, Diff.empty(),
                Map.of(), staging, staging);
    }

    private static GateContext context(String agent, Proposal proposal, Map<String, Artifact> upstream) {
        return new GateContext(node(agent), agent, proposal, null, Diff.empty(), upstream, Path.of("."), Path.of("."));
    }

    private static Node node(String agent) {
        return new Node("n", agent, Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null);
    }

    private static Proposal proposal(String rationale, List<String> derivedFrom) {
        return new Proposal(Map.of(), rationale, derivedFrom, Map.of());
    }

    private static Proposal data(Map<String, Object> data) {
        return new Proposal(Map.of(), "r", List.of("requirement"), data);
    }
}
