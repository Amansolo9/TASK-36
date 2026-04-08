package com.eaglepoint.storehub.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for IdempotencyFilter state-machine semantics:
 * - 2xx responses are committed and replayed on duplicate keys
 * - 401/403/5xx do NOT poison keys (allow retry)
 * - Concurrent duplicates get 409
 * - Requests without idempotency key pass through unaffected
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FilterChain filterChain;

    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(jdbcTemplate);
    }

    // ═══════════════════════════════════════════════════════════
    //  Pass-through: no key or GET requests
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("No idempotency key → passes through to filter chain")
    void noKey_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("GET with idempotency key → passes through (only mutations filtered)")
    void getMethod_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Idempotency-Key", "key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jdbcTemplate);
    }

    // ═══════════════════════════════════════════════════════════
    //  2xx → committed and replayed
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("First POST with 200 response → commits key as SUCCEEDED")
    @SuppressWarnings("unchecked")
    void firstPost_200_commitsKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "new-key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // No existing key
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        // Claim succeeds
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        // Simulate controller writing a 200 response
        doAnswer(inv -> {
            MockHttpServletResponse resp = (MockHttpServletResponse) inv.getArguments()[1];
            // The filter wraps response, but we can't directly access the wrapper here.
            // The wrapped response's status is set by the controller.
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        filter.doFilterInternal(request, response, filterChain);

        // Verify the key was committed (UPDATE call with SUCCEEDED)
        verify(jdbcTemplate).update(contains("UPDATE idempotency_keys SET state = 'SUCCEEDED'"),
                eq(200), anyString(), anyString());
    }

    @Test
    @DisplayName("Replay: SUCCEEDED key returns cached response without re-executing")
    @SuppressWarnings("unchecked")
    void duplicateKey_succeeded_replays() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "existing-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Key exists with SUCCEEDED state
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(Map.<String, Object>of(
                        "response_status", 201,
                        "response_body", "{\"id\":42}",
                        "state", "SUCCEEDED"
                )));

        filter.doFilterInternal(request, response, filterChain);

        // Filter chain should NOT be called (replay)
        verifyNoInteractions(filterChain);
        assertEquals(201, response.getStatus());
        assertTrue(response.getContentAsString().contains("42"));
    }

    // ═══════════════════════════════════════════════════════════
    //  401/403/5xx → key released, retries allowed
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("401 response → key released (not poisoned)")
    @SuppressWarnings("unchecked")
    void post_401_releasesKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "auth-fail-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        // Simulate 401 from downstream
        doAnswer(inv -> {
            // The filter wraps the response; the wrapper captures the status
            org.springframework.web.util.ContentCachingResponseWrapper wrapper =
                    (org.springframework.web.util.ContentCachingResponseWrapper) inv.getArguments()[1];
            wrapper.setStatus(401);
            wrapper.getWriter().write("{\"error\":\"Unauthorized\"}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        filter.doFilterInternal(request, response, filterChain);

        // Key should be released (DELETE), not committed
        verify(jdbcTemplate).update(contains("DELETE FROM idempotency_keys"), anyString());
        verify(jdbcTemplate, never()).update(contains("UPDATE idempotency_keys SET state = 'SUCCEEDED'"),
                anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("403 response → key released (not poisoned)")
    @SuppressWarnings("unchecked")
    void post_403_releasesKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "forbidden-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        doAnswer(inv -> {
            org.springframework.web.util.ContentCachingResponseWrapper wrapper =
                    (org.springframework.web.util.ContentCachingResponseWrapper) inv.getArguments()[1];
            wrapper.setStatus(403);
            wrapper.getWriter().write("{\"error\":\"Forbidden\"}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(jdbcTemplate).update(contains("DELETE FROM idempotency_keys"), anyString());
        verify(jdbcTemplate, never()).update(contains("UPDATE idempotency_keys SET state = 'SUCCEEDED'"),
                anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("500 response → key released (not poisoned)")
    @SuppressWarnings("unchecked")
    void post_500_releasesKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "server-error-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        doAnswer(inv -> {
            org.springframework.web.util.ContentCachingResponseWrapper wrapper =
                    (org.springframework.web.util.ContentCachingResponseWrapper) inv.getArguments()[1];
            wrapper.setStatus(500);
            wrapper.getWriter().write("{\"error\":\"Internal Server Error\"}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        filter.doFilterInternal(request, response, filterChain);

        verify(jdbcTemplate).update(contains("DELETE FROM idempotency_keys"), anyString());
    }

    // ═══════════════════════════════════════════════════════════
    //  Retry after transient failure
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Retry with same key after transient failure → executes business logic again")
    @SuppressWarnings("unchecked")
    void retryAfterTransientFailure_executesAgain() throws Exception {
        String idempotencyKey = "retry-key";

        // First call: simulate transient 500 (key was released after failure)
        // Second call: key is not in DB because it was released
        // → filter should proceed to claim + execute

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", idempotencyKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // No existing key (was released after previous failure)
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        // Claim succeeds
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        // Simulate successful 200 response on retry
        doAnswer(inv -> {
            org.springframework.web.util.ContentCachingResponseWrapper wrapper =
                    (org.springframework.web.util.ContentCachingResponseWrapper) inv.getArguments()[1];
            wrapper.setStatus(200);
            wrapper.getWriter().write("{\"id\":1}");
            return null;
        }).when(filterChain).doFilter(eq(request), any());

        filter.doFilterInternal(request, response, filterChain);

        // Business logic executed (filterChain called)
        verify(filterChain).doFilter(eq(request), any());
        // Key committed as SUCCEEDED
        verify(jdbcTemplate).update(contains("UPDATE idempotency_keys SET state = 'SUCCEEDED'"),
                eq(200), anyString(), anyString());
    }

    // ═══════════════════════════════════════════════════════════
    //  Concurrent duplicate → 409
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Concurrent duplicate with IN_PROGRESS key → 409 Conflict")
    @SuppressWarnings("unchecked")
    void concurrentDuplicate_inProgress_returns409() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "concurrent-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Key exists with IN_PROGRESS state (another thread is executing)
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(List.of(Map.<String, Object>of(
                        "response_status", 0,
                        "response_body", "",
                        "state", "IN_PROGRESS"
                )));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains("Duplicate request in progress"));
    }

    @Test
    @DisplayName("Race condition: claim fails, but key is now SUCCEEDED → replays")
    @SuppressWarnings("unchecked")
    void raceCondition_claimFails_succeeded_replays() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "race-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // First lookup: empty (no key)
        // After failed claim: SUCCEEDED (another thread finished first)
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(Map.<String, Object>of(
                        "response_status", 201,
                        "response_body", "{\"id\":99}",
                        "state", "SUCCEEDED"
                )));
        // Claim fails (another thread claimed first)
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(0);

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertEquals(201, response.getStatus());
        assertTrue(response.getContentAsString().contains("99"));
    }

    // ═══════════════════════════════════════════════════════════
    //  Exception during execution → key released
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("Exception during execution → key released for retry")
    @SuppressWarnings("unchecked")
    void exceptionDuringExecution_releasesKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Idempotency-Key", "exception-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("INSERT INTO idempotency_keys"), anyString()))
                .thenReturn(1);

        doThrow(new ServletException("Controller exploded"))
                .when(filterChain).doFilter(eq(request), any());

        assertThrows(ServletException.class, () ->
                filter.doFilterInternal(request, response, filterChain));

        verify(jdbcTemplate).update(contains("DELETE FROM idempotency_keys"), anyString());
    }
}
