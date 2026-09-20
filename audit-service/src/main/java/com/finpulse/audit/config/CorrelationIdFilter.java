package com.finpulse.audit.config;

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
 * The same filter as ledger-service's, duplicated rather than shared.
 *
 * <p>Extracting it into a common library would couple the two services' release
 * cycles for about forty lines of code, which is a bad trade at this size. If a third
 * service appeared, a shared starter would start to earn its keep.
 *
 * <p>Because the outbox poller sends the ledger transaction id as X-Correlation-Id,
 * the lines this service logs while storing an event carry the same id that identifies
 * the transaction in ledger-service, so one grep spans both.
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
            // See the ledger-service copy: thread-local storage plus a reused thread
            // pool means failing to clear this leaks one request's id into the next.
            MDC.remove(MDC_KEY);
        }
    }
}
