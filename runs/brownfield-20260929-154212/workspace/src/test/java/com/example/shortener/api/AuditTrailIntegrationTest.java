package com.example.shortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit trail against real H2 and Flyway: every state-changing request is recorded, reads are not. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createdAndRejectedRequestsAreAuditedWithoutPersonalData() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/audited\"}")
                        .with(request -> { request.setRemoteAddr("203.0.113.77"); return request; }))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost/admin\"}")
                        .with(request -> { request.setRemoteAddr("203.0.113.77"); return request; }))
                .andExpect(status().isBadRequest());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT client_key, method, path, status FROM audit_event WHERE path = '/api/v1/links' ORDER BY id");

        assertThat(rows).extracting(row -> row.get("STATUS")).containsSubsequence(201, 400);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("METHOD")).isEqualTo("POST");
            assertThat((String) row.get("CLIENT_KEY")).matches("[0-9a-f]{32}").doesNotContain("203.0.113.77");
        });
    }

    @Test
    void readsAreNotAudited() throws Exception {
        mvc.perform(get("/api/v1/links/unaudited/stats")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE path LIKE '%unaudited%'", Integer.class))
                .isZero();
    }
}
