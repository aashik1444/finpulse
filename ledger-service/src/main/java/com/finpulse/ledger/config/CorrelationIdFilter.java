package com.finpulse.ledger.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every log line produced during a request with one id, so all of them can be
 * found with a single grep.
 *
 * <p>An incoming X-Correlation-Id is honoured, which is what lets one id follow a
 * request across service boundaries; if absent, one is generated. The value is echoed
 * back on the response so a client can quote it in a bug report.
 *
 * <p>MDC is SLF4J's per-thread map of context values, and logback's %X{correlationId}
 * pattern reads from it as each line is written.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            // Mandatory, not defensive. MDC storage is thread-local and Tomcat reuses a
            // fixed pool of worker threads across requests. Without this, a thread that
            // handled request A would carry A's id into request B, and every line for B
            // would be tagged with the wrong id until something overwrote it, silently
            // corrupting the exact traceability this filter exists to provide.
            MDC.remove(MDC_KEY);
        }
    }
}
