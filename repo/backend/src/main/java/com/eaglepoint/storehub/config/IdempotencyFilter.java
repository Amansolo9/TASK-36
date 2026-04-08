package com.eaglepoint.storehub.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * Idempotency filter with safe retry semantics.
 *
 * State machine:
 *   IN_PROGRESS  — request is being executed; concurrent duplicates get 409
 *   SUCCEEDED    — 2xx response committed; future duplicates get replayed response
 *
 * Non-terminal failures (401, 403, 409, 5xx) are NOT persisted, so the same
 * idempotency key can be retried after a transient or auth-related failure.
 * This is critical for offline-replay scenarios where a token may have expired
 * between queue time and replay time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbcTemplate;

    /** HTTP status codes that are safe to cache and replay. */
    private static final Set<Integer> TERMINAL_SUCCESS_STATUSES = Set.of(
            200, 201, 202, 204
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader("X-Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only apply to mutation methods
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = hashKey(idempotencyKey);

        // Step 1: Check if key already exists
        Map<String, Object> existing = lookupKey(keyHash);

        if (existing != null) {
            String state = (String) existing.get("state");
            if ("SUCCEEDED".equals(state)) {
                // Replay the successful response
                int originalStatus = (Integer) existing.get("response_status");
                String body = (String) existing.get("response_body");
                log.info("Idempotency replay: key={}, originalStatus={}", keyHash.substring(0, 8), originalStatus);
                response.setStatus(originalStatus);
                response.setContentType("application/json");
                if (body != null && !body.isEmpty()) {
                    response.getWriter().write(body);
                } else {
                    response.getWriter().write("{\"replayed\":true,\"originalStatus\":" + originalStatus + "}");
                }
                return;
            } else if ("IN_PROGRESS".equals(state)) {
                // Another request with same key is currently executing
                log.warn("Idempotency conflict: key={} is IN_PROGRESS", keyHash.substring(0, 8));
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Duplicate request in progress\",\"retryable\":true}");
                return;
            }
        }

        // Step 2: Claim the key with IN_PROGRESS state
        boolean claimed = claimKey(keyHash);
        if (!claimed) {
            // Race condition: another thread claimed it between our check and insert
            // Re-check the state
            Map<String, Object> raceCheck = lookupKey(keyHash);
            if (raceCheck != null) {
                String state = (String) raceCheck.get("state");
                if ("SUCCEEDED".equals(state)) {
                    int originalStatus = (Integer) raceCheck.get("response_status");
                    String body = (String) raceCheck.get("response_body");
                    response.setStatus(originalStatus);
                    response.setContentType("application/json");
                    if (body != null && !body.isEmpty()) {
                        response.getWriter().write(body);
                    } else {
                        response.getWriter().write("{\"replayed\":true,\"originalStatus\":" + originalStatus + "}");
                    }
                    return;
                }
            }
            // IN_PROGRESS by another thread
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Duplicate request in progress\",\"retryable\":true}");
            return;
        }

        // Step 3: Execute the actual request with response caching
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, responseWrapper);

            int status = responseWrapper.getStatus();

            if (TERMINAL_SUCCESS_STATUSES.contains(status)) {
                // Step 4a: Commit successful outcome — future duplicates will be replayed
                String responseBody = new String(responseWrapper.getContentAsByteArray(),
                        responseWrapper.getCharacterEncoding() != null
                                ? responseWrapper.getCharacterEncoding() : "UTF-8");
                commitKey(keyHash, status, responseBody);
                log.debug("Idempotency key committed: key={}, status={}", keyHash.substring(0, 8), status);
            } else {
                // Step 4b: Non-success — release the key so retries are allowed
                releaseKey(keyHash);
                log.info("Idempotency key released (non-success): key={}, status={}", keyHash.substring(0, 8), status);
            }

            // Copy the cached response to the actual output
            responseWrapper.copyBodyToResponse();

        } catch (Exception e) {
            // Step 4c: Exception during execution — release key for retry
            releaseKey(keyHash);
            log.warn("Idempotency key released (exception): key={}, error={}", keyHash.substring(0, 8), e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> lookupKey(String keyHash) {
        try {
            return jdbcTemplate.query(
                    "SELECT response_status, response_body, state FROM idempotency_keys WHERE key_hash = ?",
                    (rs, rowNum) -> Map.<String, Object>of(
                            "response_status", rs.getInt("response_status"),
                            "response_body", rs.getString("response_body") != null ? rs.getString("response_body") : "",
                            "state", rs.getString("state")
                    ),
                    keyHash
            ).stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.warn("Failed to lookup idempotency key: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to claim the key with IN_PROGRESS state.
     * Returns true if this thread successfully claimed it.
     */
    private boolean claimKey(String keyHash) {
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO idempotency_keys (key_hash, response_status, state, response_body) " +
                    "VALUES (?, 0, 'IN_PROGRESS', '') ON CONFLICT (key_hash) DO NOTHING",
                    keyHash);
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to claim idempotency key: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Promotes IN_PROGRESS to SUCCEEDED with the final response.
     */
    private void commitKey(String keyHash, int status, String responseBody) {
        try {
            jdbcTemplate.update(
                    "UPDATE idempotency_keys SET state = 'SUCCEEDED', response_status = ?, response_body = ? " +
                    "WHERE key_hash = ? AND state = 'IN_PROGRESS'",
                    status, responseBody, keyHash);
        } catch (Exception e) {
            log.warn("Failed to commit idempotency key: {}", e.getMessage());
        }
    }

    /**
     * Removes the key so the same idempotency key can be retried.
     * Called on non-2xx responses or exceptions.
     */
    private void releaseKey(String keyHash) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_keys WHERE key_hash = ? AND state = 'IN_PROGRESS'", keyHash);
        } catch (Exception e) {
            log.warn("Failed to release idempotency key: {}", e.getMessage());
        }
    }

    private String hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(key.getBytes()));
        } catch (Exception e) {
            return key;
        }
    }
}
