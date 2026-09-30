package com.example.shortener.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures every request carries a unique identifier for log correlation and support.
 *
 * <p>Reads {@code X-Request-Id} from the caller if present (so a gateway or upstream service can
 * propagate its own trace), otherwise generates a UUID. The ID is placed in the MDC so it appears
 * in every log line produced during the request, and echoed back in the response header so clients
 * can include it in bug reports.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    // Whitelist: accept only URL-safe characters up to 64 chars so a caller-supplied ID cannot
    // inject extra headers into the response (HTTP response splitting via CR/LF).
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9+/=_.:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
