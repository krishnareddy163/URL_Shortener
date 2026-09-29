package com.example.agentic.agents;

import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.support.EmptyWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockAgentTest {
    @TempDir
    Path fixtures;

    @Test
    void readsFilesAndProposalMetadataForTheAttempt() throws Exception {
        write("implement/attempt2/src/main/App.java", "class App {}");
        write("implement/attempt2/proposal.json",
                "{\"rationale\":\"second try\",\"derivedFrom\":[\"design\"],\"data\":{\"k\":[1,2]}}");

        Proposal proposal = new MockAgent("developer", fixtures).propose(context("implement", 2, false, "default"));

        assertThat(proposal.files()).containsOnlyKeys("src/main/App.java");
        assertThat(proposal.rationale()).isEqualTo("second try");
        assertThat(proposal.derivedFrom()).containsExactly("design");
        assertThat(proposal.data()).containsEntry("k", List.of(1, 2));
    }

    @Test
    void missingAttemptIsAnAgentException() throws Exception {
        write("implement/attempt1/proposal.json", "{\"rationale\":\"r\"}");

        assertThatThrownBy(() -> new MockAgent("developer", fixtures).propose(context("implement", 2, false, "default")))
                .isInstanceOf(AgentException.class).hasMessageStartingWith("no fixture for attempt 2");
    }

    @Test
    void selectsTheVariantAndFallbackDirectories() throws Exception {
        write("design/https-only/attempt1/proposal.json", "{\"rationale\":\"https variant\"}");
        write("design/fallback/attempt1/proposal.json", "{\"rationale\":\"fallback round\"}");
        write("design/attempt1/proposal.json", "{\"rationale\":\"default\"}");
        MockAgent agent = new MockAgent("architect", fixtures);

        assertThat(agent.propose(context("design", 1, false, "https-only")).rationale()).isEqualTo("https variant");
        assertThat(agent.propose(context("design", 1, true, "default")).rationale()).isEqualTo("fallback round");
        assertThat(agent.propose(context("design", 1, false, "default")).rationale()).isEqualTo("default");
        assertThatThrownBy(() -> agent.propose(context("design", 1, false, "authentication")))
                .isInstanceOf(AgentException.class).hasMessageContaining("no fixture variant 'authentication'");
    }

    static AgentContext context(String node, int attempt, boolean fallback, String variant) {
        return new AgentContext("run", node, "agent", attempt, fallback, variant, "requirement", Map.of(), Map.of(), null, EmptyWorkspace.INSTANCE);
    }

    private void write(String relative, String content) throws Exception {
        Path file = fixtures.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
