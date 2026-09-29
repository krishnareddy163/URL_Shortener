package com.example.shortener.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates optional custom aliases: 3 to 32 characters of {@code [A-Za-z0-9_-]}, not a reserved word. */
@Component
public class AliasValidator {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final Set<String> RESERVED = Set.of("api", "actuator", "health", "stats");

    /** Returns the alias unchanged, or {@code null} when none was supplied. */
    public String validate(String alias) {
        if (alias == null) {
            return null;
        }
        if (!ALLOWED.matcher(alias).matches()) {
            throw new ShortenerException(ErrorCode.INVALID_ALIAS,
                    "Alias must be 3 to 32 characters of letters, digits, '_' or '-'");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new ShortenerException(ErrorCode.INVALID_ALIAS, "Alias is a reserved word");
        }
        return alias;
    }
}
