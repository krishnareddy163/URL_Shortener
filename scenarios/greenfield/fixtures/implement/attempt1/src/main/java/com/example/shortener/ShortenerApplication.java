package com.example.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** Boots the URL shortener service and provides the shared UTC clock. */
@SpringBootApplication
public class ShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortenerApplication.class, args);
    }

    /** All time access goes through this clock so tests can control it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
