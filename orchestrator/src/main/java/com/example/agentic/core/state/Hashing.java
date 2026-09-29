package com.example.agentic.core.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** SHA-256 hashing over canonical JSON (sorted keys), used for artifact, input, prompt and tree hashes. */
public final class Hashing {
    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .findAndAddModules()
            .build();

    private Hashing() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Canonical JSON: map keys and record properties sorted, no whitespace. */
    public static String canonicalJson(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("value is not JSON-serializable", exception);
        }
    }

    /** Artifact hash: SHA-256 of canonical {@code {files, data}}. Rationale and lineage are not hashed. */
    public static String artifactHash(Map<String, String> files, Map<String, Object> data) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("files", new TreeMap<>(files));
        canonical.put("data", data);
        return sha256(canonicalJson(canonical));
    }

    /** Node input hash: SHA-256 of the sorted upstream artifact hashes plus the fixture variant key. */
    public static String inputHash(List<String> upstreamHashes, String variantKey) {
        List<String> sorted = upstreamHashes.stream().sorted().toList();
        return sha256(canonicalJson(Map.of("upstream", sorted, "variant", variantKey)));
    }

    /** Constant-time comparison of two hex hashes; {@code null} equals only {@code null}. */
    public static boolean sameHash(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    public static String shortHash(String hash) {
        return hash == null ? "-" : hash.substring(0, Math.min(12, hash.length()));
    }
}
