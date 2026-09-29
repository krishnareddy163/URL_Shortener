package com.example.shortener.storage;

import com.example.shortener.domain.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** JDBC implementation of {@link AuditRepository}. Rows are only ever inserted. */
@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEvent event) {
        jdbc.update("INSERT INTO audit_event (occurred_at, client_key, method, path, status) VALUES (?, ?, ?, ?, ?)",
                LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC), event.clientKey(), event.method(),
                event.path(), event.status());
    }
}
