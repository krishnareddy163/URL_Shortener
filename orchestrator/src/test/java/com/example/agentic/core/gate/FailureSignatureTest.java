package com.example.agentic.core.gate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSignatureTest {

    @Test
    void volatileDetailsDoNotChangeTheSignature() {
        String first = "[ERROR] /tmp/run-1/staging/unit_tests-111/src/test/java/A.java:[12,5] expected 7 "
                + "at 2026-01-01T10:00:00.123Z (Time elapsed: 0.52 s)";
        String second = "[ERROR] /tmp/run-2/staging/unit_tests-999/src/test/java/A.java:[40,9] expected 7 "
                + "at 2026-03-04T11:22:33.999Z (Time elapsed: 1.07 s)";

        assertThat(FailureSignature.of("unit-tests", first)).isEqualTo(FailureSignature.of("unit-tests", second));
    }

    @Test
    void differentFailuresOrGatesHaveDifferentSignatures() {
        assertThat(FailureSignature.of("unit-tests", "expected 7 but was 8"))
                .isNotEqualTo(FailureSignature.of("unit-tests", "cannot find symbol Foo"))
                .isNotEqualTo(FailureSignature.of("compile", "expected 7 but was 8"));
    }
}
