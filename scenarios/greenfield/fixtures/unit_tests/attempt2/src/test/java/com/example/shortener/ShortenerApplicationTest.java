package com.example.shortener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ShortenerApplicationTest {

    @Test
    void mainBootsTheApplication() {
        assertThatCode(() -> ShortenerApplication.main(new String[] {
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:mem:main-launch;DB_CLOSE_DELAY=-1"
        })).doesNotThrowAnyException();
    }
}
