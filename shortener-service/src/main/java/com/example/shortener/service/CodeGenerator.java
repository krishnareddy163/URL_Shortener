package com.example.shortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Generates random seven-character base62 short codes from a cryptographically secure source. */
@Component
public class CodeGenerator {
    public static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int LENGTH = 7;

    private final RandomGenerator random;

    @Autowired
    public CodeGenerator() {
        this(new SecureRandom());
    }

    CodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String generate() {
        char[] code = new char[LENGTH];
        for (int index = 0; index < LENGTH; index++) {
            code[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
