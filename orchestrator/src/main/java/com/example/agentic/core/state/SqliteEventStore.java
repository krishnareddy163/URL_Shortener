package com.example.agentic.core.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed {@link EventStore} and {@link ArtifactStore} (one file per run).
 *
 * <p>The {@code events} table is insert-only: this class exposes no update or delete, and
 * {@code BEFORE UPDATE}/{@code BEFORE DELETE} triggers abort any attempt made through other means.
 * One connection is shared and guarded by this object's monitor (SQLite is single-writer).
 */
public final class SqliteEventStore implements EventStore, ArtifactStore {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final Connection connection;

    public SqliteEventStore(Path databaseFile) {
        try {
            Files.createDirectories(Objects.requireNonNull(databaseFile.toAbsolutePath().getParent()));
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            initialize();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (SQLException exception) {
            throw new StoreException("cannot open event store " + databaseFile, exception);
        }
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        seq          INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id       TEXT NOT NULL,
                        node_id      TEXT NULL,
                        type         TEXT NOT NULL,
                        actor        TEXT NOT NULL,
                        input_hash   TEXT NULL,
                        output_hash  TEXT NULL,
                        ts           TEXT NOT NULL,
                        details_json TEXT NOT NULL
                    )""");
            statement.execute("CREATE INDEX IF NOT EXISTS ix_events_run ON events (run_id, seq)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS artifacts (
                        hash              TEXT PRIMARY KEY,
                        node_id           TEXT NOT NULL,
                        derived_from_json TEXT NOT NULL,
                        rationale         TEXT NOT NULL,
                        content_json      TEXT NOT NULL
                    )""");
            statement.execute("""
                    CREATE TRIGGER IF NOT EXISTS events_no_update BEFORE UPDATE ON events
                    BEGIN SELECT RAISE(ABORT, 'events are append-only'); END""");
            statement.execute("""
                    CREATE TRIGGER IF NOT EXISTS events_no_delete BEFORE DELETE ON events
                    BEGIN SELECT RAISE(ABORT, 'events are append-only'); END""");
        }
    }

    @Override
    public synchronized Event append(Event draft) {
        String sql = """
                INSERT INTO events (run_id, node_id, type, actor, input_hash, output_hash, ts, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, draft.runId());
            insert.setString(2, draft.nodeId());
            insert.setString(3, draft.type().name());
            insert.setString(4, draft.actor() == null ? "engine" : draft.actor());
            insert.setString(5, draft.inputHash());
            insert.setString(6, draft.outputHash());
            insert.setString(7, draft.ts().toString());
            insert.setString(8, toJson(draft.details()));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("no sequence number generated");
                }
                return draft.withSeq(keys.getLong(1));
            }
        } catch (SQLException exception) {
            throw new StoreException("cannot append " + draft.type(), exception);
        }
    }

    @Override
    public synchronized List<Event> read(String runId) {
        String sql = """
                SELECT seq, run_id, node_id, type, actor, input_hash, output_hash, ts, details_json
                FROM events WHERE run_id = ? ORDER BY seq""";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, runId);
            List<Event> events = new ArrayList<>();
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    events.add(new Event(rows.getLong("seq"), rows.getString("run_id"), rows.getString("node_id"),
                            EventType.valueOf(rows.getString("type")), rows.getString("actor"),
                            rows.getString("input_hash"), rows.getString("output_hash"),
                            Instant.parse(rows.getString("ts")), fromJson(rows.getString("details_json"), MAP)));
                }
            }
            return List.copyOf(events);
        } catch (SQLException exception) {
            throw new StoreException("cannot read events for run " + runId, exception);
        }
    }

    @Override
    public synchronized void put(Artifact artifact) {
        String sql = """
                INSERT OR IGNORE INTO artifacts (hash, node_id, derived_from_json, rationale, content_json)
                VALUES (?, ?, ?, ?, ?)""";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("files", artifact.files());
            content.put("data", artifact.data());
            insert.setString(1, artifact.hash());
            insert.setString(2, artifact.nodeId());
            insert.setString(3, toJson(artifact.derivedFrom()));
            insert.setString(4, artifact.rationale() == null ? "" : artifact.rationale());
            insert.setString(5, toJson(content));
            insert.executeUpdate();
        } catch (SQLException exception) {
            throw new StoreException("cannot store artifact " + artifact.hash(), exception);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Optional<Artifact> get(String hash) {
        String sql = "SELECT hash, node_id, derived_from_json, rationale, content_json FROM artifacts WHERE hash = ?";
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, hash);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                Map<String, Object> content = fromJson(row.getString("content_json"), MAP);
                return Optional.of(new Artifact(row.getString("hash"), row.getString("node_id"),
                        fromJson(row.getString("derived_from_json"), STRINGS), row.getString("rationale"),
                        (Map<String, String>) content.get("files"), (Map<String, Object>) content.get("data")));
            }
        } catch (SQLException exception) {
            throw new StoreException("cannot read artifact " + hash, exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new StoreException("cannot close event store", exception);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("value is not JSON-serializable", exception);
        }
    }

    private static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("corrupt JSON in event store", exception);
        }
    }

    /** Unchecked wrapper for storage failures; the engine treats these as fatal. */
    public static final class StoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
