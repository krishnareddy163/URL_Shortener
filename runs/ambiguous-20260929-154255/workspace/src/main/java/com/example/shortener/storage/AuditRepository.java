package com.example.shortener.storage;

import com.example.shortener.domain.AuditEvent;

/** Append-only persistence port for the audit trail. */
public interface AuditRepository {

    void record(AuditEvent event);
}
