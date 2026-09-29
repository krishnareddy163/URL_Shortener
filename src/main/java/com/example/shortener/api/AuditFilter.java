package com.example.shortener.api;

import com.example.shortener.domain.AuditEvent;
import com.example.shortener.storage.AuditRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

/**
 * Records every state-changing request (POST, PUT, PATCH, DELETE) in the audit trail after it completes,
 * including rejected ones, with its time, opaque client key, method, path and final status. A failed audit write
 * is logged and does not fail the request: the response has already been decided.
 */
@Component
public class AuditFilter extends OncePerRequestFilter {
    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

    private final AuditRepository audit;
    private final ClientKeyResolver clientKeys;
    private final Clock clock;

    public AuditFilter(AuditRepository audit, ClientKeyResolver clientKeys, Clock clock) {
        this.audit = audit;
        this.clientKeys = clientKeys;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            if (STATE_CHANGING.contains(request.getMethod())) {
                record(request, response.getStatus());
            }
        }
    }

    private void record(HttpServletRequest request, int status) {
        try {
            audit.record(new AuditEvent(clock.instant(), clientKeys.keyFor(request), request.getMethod(),
                    request.getRequestURI(), status));
        } catch (RuntimeException exception) {
            log.warn("Audit event could not be recorded: {}", exception.getClass().getSimpleName());
        }
    }
}
