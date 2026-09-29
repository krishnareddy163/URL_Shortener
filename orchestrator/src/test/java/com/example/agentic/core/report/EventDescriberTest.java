package com.example.agentic.core.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventDescriberTest {

    @Test
    void buildFailuresNameTheFailingTestAndItsAssertion() {
        String reason = """
                mvn test failed (exit 1):
                [ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.034 s <<< FAILURE! -- in com.example.shortener.service.CodeGeneratorTest
                [ERROR] com.example.shortener.service.CodeGeneratorTest.generatesSevenCharacterCodes -- Time elapsed: 0.006 s <<< FAILURE!
                Expected size: 8 but was: 7 in:
                [ERROR] Failures:
                [ERROR]   CodeGeneratorTest.generatesSevenCharacterCodes:13
                Expected size: 8 but was: 7 in:
                """;

        assertThat(EventDescriber.summary(reason)).isEqualTo(
                "mvn test failed (exit 1): CodeGeneratorTest.generatesSevenCharacterCodes:13 Expected size: 8 but was: 7");
    }

    @Test
    void junitFailureMessagesDropTheExceptionClass() {
        String reason = """
                mvn test failed (exit 1):
                [ERROR] com.example.shortener.service.TrailingDotHostTest.absoluteNamesOfLocalHostsAreRejected(String)[3] -- Time elapsed: 0.003 s <<< FAILURE!
                org.opentest4j.AssertionFailedError: http://api.localhost./ must be rejected ==> Expected ShortenerException to be thrown, but nothing was thrown.
                [ERROR] Failures:
                [ERROR]   TrailingDotHostTest.absoluteNamesOfLocalHostsAreRejected:21 http://api.localhost./ must be rejected ==> Expected ShortenerException to be thrown, but nothing was thrown.
                """;

        assertThat(EventDescriber.summary(reason)).isEqualTo("mvn test failed (exit 1): "
                + "TrailingDotHostTest.absoluteNamesOfLocalHostsAreRejected:21 http://api.localhost./ must be rejected "
                + "==> Expected ShortenerException to be thrown, but nothing was thrown.");
    }

    @Test
    void singleLineReasonsAreUnchanged() {
        assertThat(EventDescriber.summary("path traversal is not allowed: ../x")).isEqualTo("path traversal is not allowed: ../x");
        assertThat(EventDescriber.summary(null)).isEmpty();
    }
}
