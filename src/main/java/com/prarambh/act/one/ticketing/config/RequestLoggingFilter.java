package com.prarambh.act.one.ticketing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every incoming HTTP request and the corresponding response.
 *
 * <p>Features:
 * <ul>
 *   <li>Correlation id support via {@code X-Request-Id} header (generated if missing)</li>
 *   <li>Logs method, path, remote address, status, and latency</li>
 *   <li>Stores request id in MDC so it appears in all log lines (if pattern includes it)</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Header used to accept/return a request correlation id. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        // Prevent log forging / multi-line entries.
        return value.replace("\r", " ").replace("\n", " ");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String remoteAddr = request.getRemoteAddr();
        String userAgent = sanitizeForLog(request.getHeader("User-Agent"));

        Instant start = Instant.now();
        try {
            log.info(
                    "event=http_in method={} path={} remoteAddr={} userAgent={}",
                    method,
                    path,
                    remoteAddr,
                    userAgent);
            filterChain.doFilter(request, response);
        } finally {
            long tookMs = Duration.between(start, Instant.now()).toMillis();
            log.info(
                    "event=http_out method={} path={} status={} tookMs={}",
                    method,
                    path,
                    response.getStatus(),
                    tookMs);
            MDC.remove("requestId");
        }
    }
}
