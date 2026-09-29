package com.example.shortener.service;

import com.example.shortener.domain.LinkStats;
import com.example.shortener.domain.ShortLink;
import com.example.shortener.storage.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Link creation, redirect resolution, and statistics.
 *
 * <p>Creation is idempotent on the normalized URL. Uniqueness of codes and URLs is enforced by the
 * database; generated-code collisions are retried up to {@value #MAX_CODE_ATTEMPTS} times.
 */
@Service
public class ShortenerService {
    public static final int MAX_CODE_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(ShortenerService.class);

    private final LinkRepository repository;
    private final UrlValidator urlValidator;
    private final AliasValidator aliasValidator;
    private final CodeGenerator codeGenerator;
    private final ClickRecorder clickRecorder;
    private final Clock clock;

    public ShortenerService(LinkRepository repository, UrlValidator urlValidator, AliasValidator aliasValidator,
                            CodeGenerator codeGenerator, ClickRecorder clickRecorder, Clock clock) {
        this.repository = repository;
        this.urlValidator = urlValidator;
        this.aliasValidator = aliasValidator;
        this.codeGenerator = codeGenerator;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
    }

    /** Creates a link, or returns the existing one for the same normalized URL when no new alias is requested. */
    public CreateResult create(String rawUrl, String rawAlias) {
        String url = urlValidator.normalize(rawUrl);
        String alias = aliasValidator.validate(rawAlias);
        String urlHash = sha256(url);
        Optional<ShortLink> existing = repository.findByUrlHash(urlHash);
        if (existing.isPresent()) {
            return reuse(existing.get(), alias);
        }
        return alias == null ? insertWithGeneratedCode(url, urlHash) : insertWithAlias(url, urlHash, alias);
    }

    /** Resolves a code for redirection and enqueues a click without blocking. */
    public ShortLink resolve(String code) {
        ShortLink link = find(code);
        clickRecorder.enqueue(code);
        return link;
    }

    public LinkStats stats(String code) {
        find(code);
        return new LinkStats(code, repository.totalClicks(code), repository.lastAccessedAt(code).orElse(null),
                repository.clicksPerDay(code));
    }

    private ShortLink find(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ShortenerException(ErrorCode.LINK_NOT_FOUND, "Short link not found"));
    }

    private CreateResult reuse(ShortLink existing, String alias) {
        if (alias == null || alias.equals(existing.code())) {
            return new CreateResult(existing, false);
        }
        throw new ShortenerException(ErrorCode.URL_ALREADY_SHORTENED,
                "URL is already shortened under a different code");
    }

    private CreateResult insertWithAlias(String url, String urlHash, String alias) {
        if (repository.findByCode(alias).isPresent()) {
            throw aliasTaken();
        }
        ShortLink link = new ShortLink(alias, url, urlHash, clock.instant());
        try {
            repository.insert(link);
            if (log.isInfoEnabled()) {
                log.info("Created short link with custom alias code={}", LogSanitizer.clean(alias));
            }
            return new CreateResult(link, true);
        } catch (DuplicateKeyException _) {
            return repository.findByUrlHash(urlHash).map(winner -> reuse(winner, alias)).orElseThrow(this::aliasTaken);
        }
    }

    private CreateResult insertWithGeneratedCode(String url, String urlHash) {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            ShortLink link = new ShortLink(codeGenerator.generate(), url, urlHash, clock.instant());
            try {
                repository.insert(link);
                if (log.isInfoEnabled()) {
                    log.info("Created short link code={}", LogSanitizer.clean(link.code()));
                }
                return new CreateResult(link, true);
            } catch (DuplicateKeyException _) {
                Optional<ShortLink> concurrent = repository.findByUrlHash(urlHash);
                if (concurrent.isPresent()) {
                    return new CreateResult(concurrent.get(), false);
                }
                log.debug("Generated code collision on attempt {}", attempt);
            }
        }
        throw new ShortenerException(ErrorCode.CODE_GENERATION_FAILED, "Could not allocate a unique short code");
    }

    private ShortenerException aliasTaken() {
        return new ShortenerException(ErrorCode.ALIAS_TAKEN, "Alias is already in use");
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /**
     * Outcome of a create request.
     *
     * @param link    the new or existing link
     * @param created {@code true} if a new link was inserted
     */
    public record CreateResult(ShortLink link, boolean created) {
    }
}
