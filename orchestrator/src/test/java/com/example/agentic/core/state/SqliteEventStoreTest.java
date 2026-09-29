package com.example.agentic.core.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteEventStoreTest {

    @TempDir
    Path dir;

    @Test
    void appendsAssignIncreasingSequenceAndRoundTripDetails() {
        try (SqliteEventStore store = new SqliteEventStore(dir.resolve("events.db"))) {
            Event first = store.append(draft(EventType.RUN_STARTED, Map.of("workflow", "demo")));
            Event second = store.append(draft(EventType.NODE_STARTED, Map.of("variant", "default", "nested", Map.of("k", List.of(1, 2)))));

            assertThat(second.seq()).isGreaterThan(first.seq());
            List<Event> read = store.read("run");
            assertThat(read).hasSize(2);
            assertThat(read.get(1).details()).containsEntry("variant", "default");
            assertThat(read.get(1).details()).containsEntry("nested", Map.of("k", List.of(1, 2)));
            assertThat(read.get(1).ts()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        }
    }

    @Test
    void updateAndDeleteAreRejectedByTriggersEvenOutsideTheDao() throws SQLException {
        Path file = dir.resolve("events.db");
        try (SqliteEventStore store = new SqliteEventStore(file)) {
            store.append(draft(EventType.RUN_STARTED, Map.of()));
        }
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = raw.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE events SET type = 'RUN_COMPLETED'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM events"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
        }
        try (SqliteEventStore reopened = new SqliteEventStore(file)) {
            assertThat(reopened.read("run")).extracting(Event::type).containsExactly(EventType.RUN_STARTED);
        }
    }

    @Test
    void artifactsAreContentAddressedAndIdempotent() {
        try (SqliteEventStore store = new SqliteEventStore(dir.resolve("events.db"))) {
            Artifact artifact = Artifact.of("design", List.of("requirements@abc"), "why", Map.of("a.txt", "A"),
                    Map.of("risks", List.of("r1")));
            store.put(artifact);
            store.put(artifact);

            assertThat(store.get(artifact.hash())).contains(artifact);
            assertThat(Hashing.artifactHash(Map.of("a.txt", "A"), Map.of("risks", List.of("r1")))).isEqualTo(artifact.hash());
            assertThat(Artifact.of("design", List.of(), "different rationale", Map.of("a.txt", "A"),
                    Map.of("risks", List.of("r1"))).hash()).as("rationale is not hashed").isEqualTo(artifact.hash());
        }
    }

    @Test
    void storeSurvivesReopen() {
        Path file = dir.resolve("events.db");
        try (SqliteEventStore store = new SqliteEventStore(file)) {
            store.append(draft(EventType.RUN_STARTED, Map.of()));
        }
        try (SqliteEventStore reopened = new SqliteEventStore(file)) {
            assertThat(reopened.read("run")).hasSize(1);
        }
    }

    private static Event draft(EventType type, Map<String, Object> details) {
        return new Event(0, "run", null, type, "engine", null, null, Instant.parse("2026-01-01T00:00:00Z"), details);
    }
}
