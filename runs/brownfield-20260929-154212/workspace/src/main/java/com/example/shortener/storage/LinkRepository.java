package com.example.shortener.storage;

import com.example.shortener.domain.DailyClicks;
import com.example.shortener.domain.ShortLink;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for links (including their optional expiry) and their click history. */
public interface LinkRepository {

    Optional<ShortLink> findByCode(String code);

    Optional<ShortLink> findByUrlHash(String urlHash);

    /**
     * Inserts a link, including its optional {@code expiresAt}.
     *
     * @throws org.springframework.dao.DuplicateKeyException if the code or URL hash already exists
     */
    void insert(ShortLink link);

    void recordClick(String code, Instant clickedAt);

    long totalClicks(String code);

    Optional<Instant> lastAccessedAt(String code);

    /** Click counts grouped by UTC date, ascending. */
    List<DailyClicks> clicksPerDay(String code);
}
