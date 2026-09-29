package com.example.agentic.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** R8: the engine core must not depend on agent implementations or the CLI; agents must not depend on the CLI. */
@AnalyzeClasses(packages = "com.example.agentic", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreDoesNotDependOnAgentsOrCli = noClasses()
            .that().resideInAPackage("com.example.agentic.core..")
            .should().dependOnClassesThat().resideInAnyPackage("com.example.agentic.agents..", "com.example.agentic.cli..");

    @ArchTest
    static final ArchRule agentsDoNotDependOnCli = noClasses()
            .that().resideInAPackage("com.example.agentic.agents..")
            .should().dependOnClassesThat().resideInAPackage("com.example.agentic.cli..");

    @ArchTest
    static final ArchRule agentsNeverWriteToTheWorkspace = noClasses()
            .that().resideInAPackage("com.example.agentic.agents..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.example.agentic.core.workspace.Workspace")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.example.agentic.core.workspace.Staging");

    /** The agent packages (registry, fixture agents, live agents, codebase scan) have no dependency cycles. */
    @ArchTest
    static final ArchRule agentPackagesAreFreeOfCycles = slices()
            .matching("com.example.agentic.agents.(**)").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule theScanDoesNotDependOnAgentImplementations = noClasses()
            .that().resideInAPackage("com.example.agentic.agents.scan..")
            .should().dependOnClassesThat().resideInAnyPackage("com.example.agentic.agents", "com.example.agentic.agents.live..");

    @ArchTest
    static final ArchRule liveAgentsDoNotDependOnTheRegistryPackage = noClasses()
            .that().resideInAPackage("com.example.agentic.agents.live..")
            .should().dependOnClassesThat().resideInAPackage("com.example.agentic.agents");
}
