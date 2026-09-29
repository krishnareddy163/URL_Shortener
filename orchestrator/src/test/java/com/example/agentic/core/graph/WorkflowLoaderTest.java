package com.example.agentic.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowLoaderTest {
    private final WorkflowLoader loader = new WorkflowLoader(
            new GraphValidator(Set.of("requirements", "architect", "developer"), Set.of("artifact-metadata", "compile")));

    @Test
    void loadsValidWorkflowWithDefaults() throws Exception {
        WorkflowGraph graph = loader.parse("""
                name: demo
                requirement: "Build it"
                nodes:
                  - id: requirements
                    agent: requirements
                  - id: design
                    agent: architect
                    task: "  Design the API.  "
                    dependsOn: [requirements]
                    exitGates: [artifact-metadata]
                    autonomy: APPROVE_AFTER
                """);

        assertThat(graph.workspace()).isEqualTo("empty");
        assertThat(graph.require("requirements").autonomy()).isEqualTo(Autonomy.AUTO);
        assertThat(graph.require("requirements").maxRetries()).isEqualTo(Node.DEFAULT_MAX_RETRIES);
        assertThat(graph.require("design").dependsOn()).containsExactly("requirements");
        assertThat(graph.require("requirements").task()).isNull();
        assertThat(graph.require("design").task()).isEqualTo("Design the API.");
        assertThat(graph.topologicalOrder()).containsExactly("requirements", "design");
    }

    @Test
    void rejectsCycleAndNamesItsMembers() {
        assertThatThrownBy(() -> loader.parse("""
                name: cyclic
                requirement: r
                nodes:
                  - {id: a, agent: architect, dependsOn: [c]}
                  - {id: b, agent: architect, dependsOn: [a]}
                  - {id: c, agent: architect, dependsOn: [b]}
                  - {id: d, agent: architect}
                """))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("dependency cycle")
                .satisfies(error -> assertThat(error.getMessage()).contains("a").contains("b").contains("c")
                        .doesNotContain("-> d"));
    }

    @Test
    void rejectsDuplicateIdsUnknownReferencesAndNegativeRetries() {
        assertThatThrownBy(() -> loader.parse("""
                name: broken
                requirement: r
                nodes:
                  - {id: a, agent: architect}
                  - {id: a, agent: architect}
                  - {id: b, agent: ghost, dependsOn: [nowhere], exitGates: [unknown-gate], maxRetries: -1}
                """))
                .isInstanceOfSatisfying(WorkflowValidationException.class, error -> assertThat(error.errors())
                        .anyMatch(message -> message.contains("duplicate node id 'a'"))
                        .anyMatch(message -> message.contains("unknown agent 'ghost'"))
                        .anyMatch(message -> message.contains("unknown node 'nowhere'"))
                        .anyMatch(message -> message.contains("unknown exit gate 'unknown-gate'"))
                        .anyMatch(message -> message.contains("negative maxRetries")));
    }

    @Test
    void rejectsUnknownFieldsSoTyposCannotDropDependencies() {
        assertThatThrownBy(() -> loader.parse("""
                name: typo
                requirement: r
                nodes:
                  - {id: a, agent: architect}
                  - {id: b, agent: architect, dependOn: [a]}
                """))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("unknown field 'dependOn'");
    }

    @Test
    void rejectsBadWorkspaceAndBudgets() {
        assertThatThrownBy(() -> loader.parse("""
                name: bad
                requirement: r
                workspace: "/etc"
                budgets: {maxAgentCalls: 0, maxCoffee: 3}
                nodes:
                  - {id: a, agent: architect}
                """))
                .isInstanceOfSatisfying(WorkflowValidationException.class, error -> assertThat(error.errors())
                        .anyMatch(message -> message.contains("workspace must be"))
                        .anyMatch(message -> message.contains("'maxAgentCalls' must be positive"))
                        .anyMatch(message -> message.contains("unknown budget 'maxCoffee'")));
    }

    @Test
    void downstreamAndUpstreamAreTransitive() throws Exception {
        WorkflowGraph graph = loader.parse("""
                name: diamond
                requirement: r
                nodes:
                  - {id: a, agent: architect}
                  - {id: b, agent: architect, dependsOn: [a]}
                  - {id: c, agent: architect, dependsOn: [a]}
                  - {id: d, agent: architect, dependsOn: [b, c]}
                """);

        assertThat(graph.downstreamOf("a")).containsExactly("b", "c", "d");
        assertThat(graph.upstreamOf("d")).containsExactly("a", "b", "c");
        assertThat(graph.downstreamOf("d")).isEmpty();
    }
}
