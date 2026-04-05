package com.eaglepoint.storehub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for authentication endpoints.
 * Limits to 10 requests per minute per IP for /api/auth/** paths.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_MAX_REQUESTS = 10;
    private static final int GENERAL_MAX_REQUESTS = 60;
    private static final long WINDOW_MS = 60_000;

    private static final java.util.Set<String> STRICT_PATHS = java.util.Set.of(
            "/api/auth/", "/api/community/posts/", "/api/analytics/events"
    );

    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isStrictPath = STRICT_PATHS.stream().anyMatch(path::startsWith);
        if (!isStrictPath) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxRequests = path.startsWith("/api/auth/") ? AUTH_MAX_REQUESTS : GENERAL_MAX_REQUESTS;

        String ip = getClientIp(request);
        RateWindow window = windows.compute(ip, (k, v) -> {
            long now = System.currentTimeMillis();
            if (v == null || now - v.startTime > WINDOW_MS) {
                return new RateWindow(now, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });

        if (window.count.get() > maxRequests) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too many requests. Try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);

        // Periodic cleanup of expired windows
        if (windows.size() > 1000) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(e -> now - e.getValue().startTime > WINDOW_MS);
        }
    }

    /**
     * Returns client IP. Only trusts X-Forwarded-For when the direct connection
     * comes from a known reverse proxy (Docker internal or loopback).
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        // Only trust XFF from known reverse proxies (nginx in Docker, or loopback)
        if (remoteAddr != null && (remoteAddr.startsWith("172.") || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("192.168.") || "127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr))) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private static class RateWindow {
        final long startTime;
        final AtomicInteger count;

        RateWindow(long startTime, AtomicInteger count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
