package com.eaglepoint.storehub.security;

import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final long idleTimeoutMs;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomUserDetailsService userDetailsService,
                                   UserRepository userRepository,
                                   AuditService auditService,
                                   @Value("${app.security.idle-timeout-ms}") long idleTimeoutMs) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            if (tokenProvider.validateToken(token)) {
                Long userId = tokenProvider.getUserIdFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                if (!userDetails.isEnabled()) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Check server-side idle timeout
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    Instant lastActivity = user.getLastActivityAt();
                    if (lastActivity != null) {
                        long idleTime = Instant.now().toEpochMilli() - lastActivity.toEpochMilli();
                        if (idleTime > idleTimeoutMs) {
                            log.warn("Idle timeout exceeded for userId={}. Idle for {}ms, threshold={}ms",
                                    userId, idleTime, idleTimeoutMs);
                            SecurityContextHolder.clearContext();
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"error\":\"Session expired due to inactivity\"}");
                            response.setContentType("application/json");
                            return;
                        }
                    }

                    // Check token version for revocation
                    long jwtTokenVersion = tokenProvider.getTokenVersionFromToken(token);
                    if (jwtTokenVersion != user.getTokenVersion()) {
                        log.warn("Token version mismatch for userId={}: jwt={}, db={}",
                                userId, jwtTokenVersion, user.getTokenVersion());
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Token has been revoked\"}");
                        return;
                    }

                    // Update lastActivityAt, but skip DB write if updated within last 60s to reduce load
                    Instant now = Instant.now();
                    if (lastActivity == null || now.toEpochMilli() - lastActivity.toEpochMilli() > 60_000) {
                        user.setLastActivityAt(now);
                        userRepository.save(user);
                        try {
                            auditService.logSystemAction("SESSION_ACTIVITY", "User", userId,
                                    "Session activity updated");
                        } catch (Exception ignored) {
                            // Non-critical — don't fail the request for audit logging
                        }
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Sliding session: refresh token when past half its lifetime
                long authTime = tokenProvider.getAuthTimeFromToken(token);
                long halfLife = idleTimeoutMs / 2;
                if (Instant.now().toEpochMilli() - authTime > halfLife) {
                    String refreshedToken = tokenProvider.generateToken((UserPrincipal) userDetails);
                    response.setHeader("X-Refreshed-Token", refreshedToken);
                }

                log.debug("JWT authentication successful for userId={}", userId);
            } else {
                log.warn("Invalid JWT token received from {}", request.getRemoteAddr());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
