package com.example.agentic.core.gate;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReproducesDefectGateTest {
    private static final Set<String> ADDED = Set.of("com.example.TrailingDotHostTest");

    private static GateResult judge(int exitCode, String output) {
        return ReproducesDefectGate.judge(new BuildRunner.BuildResult(exitCode, false, output), ADDED);
    }

    private static String reason(GateResult result) {
        return ((GateResult.Fail) result).reason();
    }

    @Test
    void passesWhenOnlyTheNewTestFailsOnAnAssertion() {
        assertThat(judge(1, "[ERROR] Tests run: 6, Failures: 3 <<< FAILURE! -- in com.example.TrailingDotHostTest"))
                .isEqualTo(GateResult.pass());
    }

    @Test
    void failsWhenTheNewTestPassesOnTheUnfixedCode() {
        assertThat(reason(judge(0, ""))).contains("do not reproduce the defect");
    }

    @Test
    void failsWhenTheBuildDoesNotCompile() {
        assertThat(reason(judge(1, "[ERROR] COMPILATION ERROR :\n[ERROR] Foo.java:[3,5] cannot find symbol")))
                .contains("fails to compile");
    }

    @Test
    void failsWhenExistingTestsBreakToo() {
        String output = "<<< FAILURE! -- in com.example.TrailingDotHostTest\n<<< ERROR! -- in com.example.UrlValidatorTest";
        assertThat(reason(judge(1, output))).contains("tests outside the reproduction fail too [com.example.UrlValidatorTest]");
    }

    @Test
    void failsWhenTheBuildFailsWithoutAFailingTest() {
        assertThat(reason(judge(1, "[ERROR] Could not resolve dependencies"))).contains("without a failing test");
    }

    @Test
    void findsTestClassesAddedUnderTheTestRoot() {
        assertThat(ReproducesDefectGate.addedTestClasses(Set.of("src/test/java/a/b/FooTest.java",
                "src/test/java/a/b/Helper.java", "src/main/java/a/b/BarTest.java")))
                .containsExactly("a.b.FooTest");
    }
}
