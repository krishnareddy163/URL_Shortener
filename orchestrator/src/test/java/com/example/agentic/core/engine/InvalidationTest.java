package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Clarifications, answer folding, and transitive hash invalidation with approval revocation and file revert. */
class InvalidationTest {

    @TempDir
    Path dir;

    private final ScriptedAgent requirements = new ScriptedAgent("requirements", context -> new Proposal(Map.of(),
            "normalized", List.of(), Map.of(
            "problemStatement", "secure links",
            "acceptanceCriteria", List.of("links are secure"),
            "assumptions", List.of("single region"),
            "ambiguities", List.of(
                    Map.of("id", "q-secure", "question", "what is secure?", "blocking", true,
                            "options", List.of("https-only", "blocklist")),
                    Map.of("id", "q-metrics", "question", "which metrics?", "blocking", false,
                            "assumptionIfUnanswered", "counts only")))));

    private final ScriptedAgent worker = new ScriptedAgent("worker", context -> new Proposal(
            Map.of(context.nodeId() + "-" + context.variantKey() + ".txt", "for " + context.variantKey()),
            "variant " + context.variantKey(), List.copyOf(context.upstream().keySet()), Map.of()));

    private Orchestrator open() {
        return new TestEngine().agent(requirements).agent(worker).open(dir, new InMemoryEventStore());
    }

    private static List<Node> nodes() {
        return List.of(
                new Node("requirements", "requirements", Set.of(), List.of(), List.of(), Autonomy.AUTO, 0, null, null),
                new Node("design", "worker", Set.of("requirements"), List.of(), List.of("artifact-metadata"), Autonomy.AUTO, 0, null, "q-secure"),
                new Node("build", "worker", Set.of("design"), List.of(), List.of("artifact-metadata"), Autonomy.AUTO, 0, null, "q-secure"),
                new Node("release", "worker", Set.of("build"), List.of(), List.of(), Autonomy.APPROVE_AFTER, 0, null, "q-secure"));
    }

    @Test
    void blockingQuestionPausesAndTheAnswerIsFoldedIntoTheArtifact() throws Exception {
        try (Orchestrator orchestrator = open()) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(nodes().toArray(Node[]::new)), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.PAUSED);
            assertThat(orchestrator.state().status("requirements")).isEqualTo(NodeStatus.AWAITING_CLARIFICATION);
            assertThat(orchestrator.clarifications().openQuestions()).extracting(q -> q.id()).containsExactly("q-secure");

            orchestrator.clarifications().answer("q-secure", "https-only", "carol");

            Artifact artifact = currentArtifact(orchestrator, "requirements");
            assertThat(artifact.data()).containsEntry("answers", Map.of("q-secure", "https-only"));
            assertThat(artifact.data().get("assumptions")).asInstanceOf(InstanceOfAssertFactories.list(String.class))
                    .contains("single region", "q-metrics: counts only");
            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.PAUSED);
            assertThat(Files.exists(orchestrator.workspace().root().resolve("build-https-only.txt"))).isTrue();
        }
    }

    @Test
    void changingAnAnswerInvalidatesDownstreamTransitivelyRevertsFilesAndRevokesApprovals() throws Exception {
        try (Orchestrator orchestrator = open()) {
            orchestrator.start(TestEngine.graph(nodes().toArray(Node[]::new)), dir, RunMetadata.none());
            orchestrator.clarifications().answer("q-secure", "https-only", "carol");
            orchestrator.resume();
            orchestrator.approvals().approve("release", "dave", "ship", null);
            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.COMPLETED);
            String oldRequirementsHash = orchestrator.state().node("requirements").currentHash();

            orchestrator.clarifications().answer("q-secure", "blocklist", "carol");

            List<Event> invalidated = orchestrator.log().events().stream()
                    .filter(event -> event.type() == EventType.INVALIDATED).toList();
            assertThat(invalidated).extracting(Event::nodeId).containsExactly("design", "build", "release");
            assertThat(invalidated).allSatisfy(event -> assertThat(event.payload(Payload.Invalidated.class).oldHash()).isEqualTo(oldRequirementsHash));
            assertThat(invalidated.getLast().payload(Payload.Invalidated.class).approvalRevoked()).isTrue();
            assertThat(orchestrator.state().node("release").approvedHash()).isNull();
            assertThat(orchestrator.log().events()).anyMatch(event -> event.type() == EventType.REPLAN
                    && event.payload(Payload.Replan.class).isInvalidation()
                    && event.payload(Payload.Replan.class).cascade().size() == 3);
            assertThat(Files.exists(orchestrator.workspace().root().resolve("build-https-only.txt")))
                    .as("invalidated work is reverted from the workspace").isFalse();

            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.PAUSED);
            orchestrator.approvals().approve("release", "dave", "ship v2", null);
            assertThat(orchestrator.resume()).isEqualTo(RunOutcome.COMPLETED);
            assertThat(Files.exists(orchestrator.workspace().root().resolve("build-blocklist.txt"))).isTrue();
            assertThat(worker.calls("design")).isEqualTo(2);
        }
    }

    @Test
    void reAnsweringWithTheSameValueIsANoOp() throws Exception {
        try (Orchestrator orchestrator = open()) {
            orchestrator.start(TestEngine.graph(nodes().toArray(Node[]::new)), dir, RunMetadata.none());
            orchestrator.clarifications().answer("q-secure", "https-only", "carol");
            orchestrator.resume();
            String hash = orchestrator.state().node("requirements").currentHash();

            String result = orchestrator.clarifications().answer("q-secure", "https-only", "carol");

            assertThat(result).contains("unchanged");
            assertThat(orchestrator.state().node("requirements").currentHash()).isEqualTo(hash);
            assertThat(orchestrator.log().events()).noneMatch(event -> event.type() == EventType.INVALIDATED);
        }
    }

    private static Artifact currentArtifact(Orchestrator orchestrator, String node) {
        return orchestrator.artifacts().get(orchestrator.state().node(node).currentHash()).orElseThrow();
    }
}
