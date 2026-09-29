package com.example.shortener.storage;

import com.example.shortener.domain.DailyClicks;
import com.example.shortener.domain.ShortLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link LinkRepository}. Timestamps are stored as UTC wall-clock values. */
@Repository
public class JdbcLinkRepository implements LinkRepository {
    private static final RowMapper<ShortLink> LINK_MAPPER = (row, index) -> new ShortLink(
            row.getString("code"), row.getString("url"), row.getString("url_hash"),
            toInstant(row.getObject("created_at", LocalDateTime.class)),
            nullableInstant(row.getObject("expires_at", LocalDateTime.class)));

    private final JdbcTemplate jdbc;

    public JdbcLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ShortLink> findByCode(String code) {
        return jdbc.query("SELECT code, url, url_hash, created_at, expires_at FROM link WHERE code = ?", LINK_MAPPER, code)
                .stream().findFirst();
    }

    @Override
    public Optional<ShortLink> findByUrlHash(String urlHash) {
        return jdbc.query("SELECT code, url, url_hash, created_at, expires_at FROM link WHERE url_hash = ?", LINK_MAPPER, urlHash)
                .stream().findFirst();
    }

    @Override
    public void insert(ShortLink link) {
        jdbc.update("INSERT INTO link (code, url, url_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                link.code(), link.url(), link.urlHash(), toUtc(link.createdAt()),
                link.expiresAt() == null ? null : toUtc(link.expiresAt()));
    }

    @Override
    public void recordClick(String code, Instant clickedAt) {
        jdbc.update("INSERT INTO click (code, clicked_at) VALUES (?, ?)", code, toUtc(clickedAt));
    }

    @Override
    public long totalClicks(String code) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM click WHERE code = ?", Long.class, code);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Instant> lastAccessedAt(String code) {
        LocalDateTime last = jdbc.queryForObject("SELECT MAX(clicked_at) FROM click WHERE code = ?",
                LocalDateTime.class, code);
        return Optional.ofNullable(last).map(JdbcLinkRepository::toInstant);
    }

    @Override
    public List<DailyClicks> clicksPerDay(String code) {
        return jdbc.query("""
                        SELECT CAST(clicked_at AS DATE) AS click_date, COUNT(*) AS click_count
                        FROM click WHERE code = ?
                        GROUP BY CAST(clicked_at AS DATE)
                        ORDER BY click_date
                        """,
                (row, index) -> new DailyClicks(row.getObject("click_date", LocalDate.class),
                        row.getLong("click_count")),
                code);
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }

    private static Instant nullableInstant(LocalDateTime utc) {
        return utc == null ? null : toInstant(utc);
    }
}
