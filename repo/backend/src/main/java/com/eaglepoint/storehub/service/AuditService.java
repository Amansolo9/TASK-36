package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.AuditLog;
import com.eaglepoint.storehub.repository.AuditLogRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, String entityType, Long entityId,
                          Object beforeState, Object afterState) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId);

            // Get current user from SecurityContext
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                builder.userId(principal.getId());
                builder.username(principal.getUsername());
            }

            // Get request details
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                // Device evidence: use explicit header or derive from request metadata
                String deviceHeader = request.getHeader("X-Device-Fingerprint");
                if (deviceHeader != null && !deviceHeader.isBlank()) {
                    builder.deviceFingerprint(deviceHeader);
                } else {
                    // Derive stable device evidence from IP + User-Agent
                    String ua = request.getHeader("User-Agent");
                    String derived = request.getRemoteAddr() + "|" + (ua != null ? ua : "unknown");
                    try {
                        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                        byte[] hash = md.digest(derived.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        builder.deviceFingerprint(java.util.HexFormat.of().formatHex(hash).substring(0, 16));
                    } catch (Exception ignored) {
                        builder.deviceFingerprint("derived:" + derived.hashCode());
                    }
                }
                builder.ipAddress(request.getRemoteAddr());
            }

            // Store before/after states — accept both pre-serialized strings and objects
            if (beforeState != null) {
                builder.beforeState(beforeState instanceof String s ? s : objectMapper.writeValueAsString(beforeState));
            }
            if (afterState != null) {
                builder.afterState(afterState instanceof String s ? s : objectMapper.writeValueAsString(afterState));
            }

            auditLogRepository.save(builder.build());
        } catch (Exception e) {
            log.error("Failed to write audit log for action={} entityType={} entityId={}",
                    action, entityType, entityId, e);
        }
    }

    /**
     * Logs an audit entry for system-driven (scheduler/background) writes
     * where no SecurityContext or HTTP request exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemAction(String action, String entityType, Long entityId, String description) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .username("SYSTEM")
                    .afterState(description)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write system audit log for action={} entityType={} entityId={}",
                    action, entityType, entityId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrail(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrailByUser(Long userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditTrailByDateRange(Instant start, Instant end) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }
}
