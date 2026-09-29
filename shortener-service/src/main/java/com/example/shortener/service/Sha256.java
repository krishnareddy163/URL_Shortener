package com.example.shortener.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 digests of UTF-8 text. Every Java platform must provide SHA-256, so the checked exception cannot occur in
 * practice; it is handled here once instead of at every call site.
 */
public final class Sha256 {

    private Sha256() {
    }

    public static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
