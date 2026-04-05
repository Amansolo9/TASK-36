package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.dto.AuditLogDto;
import com.eaglepoint.storehub.entity.AuditLog;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.CheckInRepository;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.service.AuditService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final SiteAuthorizationService siteAuth;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SupportTicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<AuditLogDto>> getAuditTrail(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        enforceEntityScope(entityType, entityId);
        List<AuditLogDto> trail = auditService.getAuditTrail(entityType, entityId).stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(trail);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<AuditLogDto>> getAuditTrailByUser(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        siteAuth.requireSiteAccess(user.getSite() != null ? user.getSite().getId() : null);
        List<AuditLogDto> trail = auditService.getAuditTrailByUser(userId).stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(trail);
    }

    @GetMapping("/range")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    public ResponseEntity<List<AuditLogDto>> getAuditTrailByDateRange(
            @RequestParam Instant start,
            @RequestParam Instant end) {
        List<AuditLogDto> trail = auditService.getAuditTrailByDateRange(start, end).stream()
                .map(this::toDto).toList();
        return ResponseEntity.ok(trail);
    }

    private void enforceEntityScope(String entityType, Long entityId) {
        boolean isAdmin = false;
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.eaglepoint.storehub.security.UserPrincipal p) {
            isAdmin = "ENTERPRISE_ADMIN".equals(p.getRole());
        }

        switch (entityType) {
            case "Order" -> {
                var order = orderRepository.findById(entityId);
                if (order.isPresent()) {
                    siteAuth.requireSiteAccess(order.get().getSite().getId());
                } else if (!isAdmin) {
                    throw new com.eaglepoint.storehub.config.AccessDeniedException(
                            "Cannot verify scope for deleted/missing entity");
                }
            }
            case "SupportTicket" -> {
                var ticket = ticketRepository.findById(entityId);
                if (ticket.isPresent()) {
                    siteAuth.requireSiteAccess(ticket.get().getOrder().getSite().getId());
                } else if (!isAdmin) {
                    throw new com.eaglepoint.storehub.config.AccessDeniedException(
                            "Cannot verify scope for deleted/missing entity");
                }
            }
            case "CheckIn" -> {
                var checkIn = checkInRepository.findById(entityId);
                if (checkIn.isPresent()) {
                    siteAuth.requireSiteAccess(checkIn.get().getSite().getId());
                } else if (!isAdmin) {
                    throw new com.eaglepoint.storehub.config.AccessDeniedException(
                            "Cannot verify scope for deleted/missing entity");
                }
            }
            default -> {
                if (!isAdmin) {
                    throw new com.eaglepoint.storehub.config.AccessDeniedException(
                            "Audit lookup for entity type '" + entityType + "' requires enterprise admin access");
                }
            }
        }
    }

    private AuditLogDto toDto(AuditLog log) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(log.getId());
        dto.setUserId(log.getUserId());
        dto.setUsername(log.getUsername());
        dto.setDeviceFingerprint(log.getDeviceFingerprint());
        dto.setAction(log.getAction());
        dto.setEntityType(log.getEntityType());
        dto.setEntityId(log.getEntityId());
        dto.setBeforeState(log.getBeforeState());
        dto.setAfterState(log.getAfterState());
        dto.setIpAddress(log.getIpAddress());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
