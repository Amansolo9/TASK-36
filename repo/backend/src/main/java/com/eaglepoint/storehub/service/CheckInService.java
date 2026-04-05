package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.CheckInRequest;
import com.eaglepoint.storehub.dto.CheckInResponse;
import com.eaglepoint.storehub.entity.*;
import com.eaglepoint.storehub.enums.CheckInStatus;
import com.eaglepoint.storehub.repository.*;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final Duration EARLY_WINDOW = Duration.ofMinutes(15);
    private static final Duration LATE_WINDOW = Duration.ofMinutes(10);
    private static final int FRAUD_ATTEMPT_THRESHOLD = 5;
    private static final Duration FRAUD_WINDOW = Duration.ofMinutes(10);
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(60);

    private final CheckInRepository checkInRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final DeviceBindingRepository deviceBindingRepository;
    private final SiteAuthorizationService siteAuth;

    @Audited(action = "CHECK_IN", entityType = "CheckIn")
    @Transactional
    public CheckInResponse checkIn(Long userId, CheckInRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Organization site = organizationRepository.findById(request.getSiteId())
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));

        siteAuth.requireSiteAccess(request.getSiteId());

        // Team scope enforcement: if user has a team, verify it belongs to the site
        if (user.getTeam() != null && user.getSite() != null
                && !user.getSite().getId().equals(request.getSiteId())) {
            throw new com.eaglepoint.storehub.config.AccessDeniedException(
                    "Check-in denied: user's team is not assigned to this site");
        }

        Instant now = Instant.now();

        // Device fingerprint augmentation (computed early for use in flagged records)
        String normalizedFingerprint = augmentFingerprint(request.getDeviceFingerprint());

        // Server-derived schedule — never trust client scheduledTime
        LocalDate today = LocalDate.now();
        Optional<ShiftAssignment> shiftOpt = shiftAssignmentRepository.findActiveShift(userId, request.getSiteId(), today);

        Instant scheduled;
        if (shiftOpt.isPresent()) {
            ShiftAssignment shift = shiftOpt.get();
            scheduled = shift.getShiftStart().atDate(today).atZone(ZoneId.systemDefault()).toInstant();
        } else {
            // No shift assigned — persist flagged record and reject check-in
            CheckIn flagged = checkInRepository.save(CheckIn.builder()
                    .user(user).site(site).timestamp(now).scheduledTime(now)
                    .deviceFingerprint(normalizedFingerprint)
                    .locationDescription(request.getLocationDescription())
                    .status(CheckInStatus.FLAGGED).build());
            fraudAlertRepository.save(FraudAlert.builder()
                    .user(user).reason("NO_ACTIVE_SHIFT")
                    .details("Check-in attempted with no active shift assignment for " + java.time.LocalDate.now())
                    .build());
            log.warn("Check-in rejected: no active shift for userId={}, siteId={}", userId, request.getSiteId());
            return buildResponse(flagged.getId(), userId, request.getSiteId(), now, now,
                    CheckInStatus.FLAGGED, "No active shift assignment found for today",
                    request.getLocationDescription(), normalizedFingerprint);
        }

        // Fraud detection: >5 attempts in 10 minutes
        long recentAttempts = checkInRepository.countByUserIdSince(userId, now.minus(FRAUD_WINDOW));
        if (recentAttempts >= FRAUD_ATTEMPT_THRESHOLD) {
            fraudAlertRepository.save(FraudAlert.builder()
                    .user(user)
                    .reason("EXCESSIVE_CHECKIN_ATTEMPTS")
                    .details("User attempted " + (recentAttempts + 1) + " check-ins within 10 minutes")
                    .build());

            log.warn("Fraud detection: excessive check-in attempts ({}) by userId={} within 10 minutes", recentAttempts + 1, userId);
            // Persist flagged check-in with raw evidence for supervisor review
            CheckIn flagged = checkInRepository.save(CheckIn.builder()
                    .user(user).site(site).timestamp(now).scheduledTime(scheduled)
                    .deviceFingerprint(normalizedFingerprint)
                    .locationDescription(request.getLocationDescription())
                    .status(CheckInStatus.FLAGGED).build());
            return buildResponse(flagged.getId(), userId, request.getSiteId(), now, scheduled,
                    CheckInStatus.FLAGGED, "Too many check-in attempts. Your account has been flagged.");
        }

        // Duplicate check-in blocking: no valid check-in within 60 minutes
        long recentValid = checkInRepository.countValidCheckInsSince(
                userId, request.getSiteId(), now.minus(DUPLICATE_WINDOW));
        if (recentValid > 0) {
            log.warn("Duplicate check-in blocked for userId={} at siteId={}", userId, request.getSiteId());
            CheckIn flagged = checkInRepository.save(CheckIn.builder()
                    .user(user).site(site).timestamp(now).scheduledTime(scheduled)
                    .deviceFingerprint(normalizedFingerprint)
                    .locationDescription(request.getLocationDescription())
                    .status(CheckInStatus.FLAGGED).build());
            // Create linked fraud alert requiring supervisor resolution
            fraudAlertRepository.save(FraudAlert.builder()
                    .user(user).reason("DUPLICATE_CHECKIN")
                    .details("Duplicate check-in within 60 minutes at site " + request.getSiteId())
                    .build());
            return buildResponse(flagged.getId(), userId, request.getSiteId(), now, scheduled,
                    CheckInStatus.FLAGGED, "Duplicate check-in blocked. A valid check-in exists within the last 60 minutes.");
        }

        // Device binding check
        if (normalizedFingerprint != null) {
            List<DeviceBinding> bindings = deviceBindingRepository.findByUserIdAndActiveTrue(userId);
            if (bindings.isEmpty()) {
                // First device — auto-bind
                deviceBindingRepository.save(DeviceBinding.builder()
                        .user(user).deviceHash(normalizedFingerprint).deviceLabel("Auto-bound").build());
            } else {
                boolean knownDevice = bindings.stream().anyMatch(b -> b.getDeviceHash().equals(normalizedFingerprint));
                if (!knownDevice) {
                    log.warn("Device mismatch: userId={}, bound devices={}", userId, bindings.size());
                    fraudAlertRepository.save(FraudAlert.builder()
                            .user(user).reason("DEVICE_MISMATCH")
                            .details("Unrecognized device hash: " + normalizedFingerprint.substring(0, 12) + "...").build());
                }
            }
        }

        // Window validation: 15m before to 10m after server-derived scheduled time
        CheckInStatus status;
        String message;
        Instant windowStart = scheduled.minus(EARLY_WINDOW);
        Instant windowEnd = scheduled.plus(LATE_WINDOW);

        if (now.isBefore(windowStart)) {
            status = CheckInStatus.FLAGGED;
            message = "Check-in rejected: outside allowed window (too early).";
        } else if (now.isAfter(windowEnd)) {
            status = CheckInStatus.FLAGGED;
            message = "Check-in rejected: outside allowed window (too late).";
        } else {
            status = CheckInStatus.VALID;
            message = "Check-in successful.";
        }

        CheckIn checkIn = CheckIn.builder()
                .user(user)
                .site(site)
                .timestamp(now)
                .scheduledTime(scheduled)
                .deviceFingerprint(normalizedFingerprint)
                .locationDescription(request.getLocationDescription())
                .status(status)
                .build();

        checkIn = checkInRepository.save(checkIn);

        if (status == CheckInStatus.VALID) {
            log.info("Valid check-in recorded: userId={}, siteId={}, checkInId={}", userId, request.getSiteId(), checkIn.getId());
        } else if (status == CheckInStatus.FLAGGED) {
            // Create linked fraud alert for out-of-window flagged check-ins
            fraudAlertRepository.save(FraudAlert.builder()
                    .user(user).reason("OUT_OF_WINDOW")
                    .details("Check-in outside allowed window: " + message)
                    .build());
        }

        return buildResponse(checkIn.getId(), userId, request.getSiteId(), now, scheduled, status, message,
                request.getLocationDescription(), normalizedFingerprint);
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<CheckInResponse> getCheckInsBySite(Long siteId, Instant start, Instant end) {
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null && !visibleSites.contains(siteId)) {
            throw new IllegalArgumentException("Access denied: site not in your data scope");
        }
        return checkInRepository.findBySiteAndTimeRange(siteId, start, end).stream()
                .map(c -> buildResponse(c.getId(), c.getUser().getId(), c.getSite().getId(),
                        c.getTimestamp(), c.getScheduledTime(), c.getStatus(), null))
                .toList();
    }

    /**
     * Augments client-provided fingerprint with server-side data (IP + User-Agent hash)
     * to make it harder to forge.
     */
    private String augmentFingerprint(String clientFingerprint) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String serverPart = "";
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getRemoteAddr();
                String ua = req.getHeader("User-Agent");
                serverPart = ip + "|" + (ua != null ? ua : "");
            }
            String combined = (clientFingerprint != null ? clientFingerprint : "") + "|" + serverPart;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return clientFingerprint;
        }
    }

    @Transactional(readOnly = true)
    public List<FraudAlert> getUnresolvedAlerts() {
        List<FraudAlert> allAlerts = fraudAlertRepository.findByResolvedFalseOrderByCreatedAtDesc();
        // Enterprise admin sees all; others see only in-scope site alerts
        return allAlerts.stream().filter(alert -> {
            Long alertSiteId = null;
            // Derive site from the user's site assignment
            if (alert.getUser() != null && alert.getUser().getSite() != null) {
                alertSiteId = alert.getUser().getSite().getId();
            }
            return siteAuth.canAccessSite(alertSiteId);
        }).toList();
    }

    @Audited(action = "RESOLVE_FRAUD_ALERT", entityType = "FraudAlert")
    @Transactional
    public FraudAlert resolveFraudAlert(Long alertId, Long resolverId, String note) {
        String trimmedNote = note != null ? note.trim() : "";
        if (trimmedNote.length() < 10) {
            throw new IllegalArgumentException("Supervisor resolution note must be at least 10 characters");
        }

        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud alert not found"));

        // Enforce site-scope on resolution — manager must have access to the alert's site
        Long alertSiteId = (alert.getUser() != null && alert.getUser().getSite() != null)
                ? alert.getUser().getSite().getId() : null;
        siteAuth.requireSiteAccess(alertSiteId);

        User resolver = userRepository.findById(resolverId)
                .orElseThrow(() -> new IllegalArgumentException("Resolver not found"));

        alert.setResolved(true);
        alert.setResolverNote(trimmedNote);
        alert.setResolvedBy(resolver);
        alert.setResolvedAt(Instant.now());

        log.info("Fraud alert resolved: alertId={}, resolverId={}", alertId, resolverId);
        return fraudAlertRepository.save(alert);
    }

    private CheckInResponse buildResponse(Long id, Long userId, Long siteId,
                                           Instant timestamp, Instant scheduled,
                                           CheckInStatus status, String message) {
        return buildResponse(id, userId, siteId, timestamp, scheduled, status, message, null, null);
    }

    private CheckInResponse buildResponse(Long id, Long userId, Long siteId,
                                           Instant timestamp, Instant scheduled,
                                           CheckInStatus status, String message,
                                           String locationDescription, String deviceHash) {
        // Compute timing classification independently from status
        // This ensures FLAGGED check-ins still show correct timing (EARLY/LATE/ON_TIME)
        String windowClass;
        if (scheduled != null && timestamp != null) {
            Instant windowStart = scheduled.minus(EARLY_WINDOW);
            Instant windowEnd = scheduled.plus(LATE_WINDOW);
            if (timestamp.isBefore(windowStart)) {
                windowClass = "EARLY";
            } else if (timestamp.isAfter(windowEnd)) {
                windowClass = "LATE";
            } else {
                windowClass = "ON_TIME";
            }
        } else {
            windowClass = "UNKNOWN";
        }

        String maskedDevice = deviceHash != null && deviceHash.length() > 8
                ? deviceHash.substring(0, 8) + "..." : null;

        return CheckInResponse.builder()
                .id(id).userId(userId).siteId(siteId)
                .timestamp(timestamp).scheduledTime(scheduled)
                .status(status).message(message)
                .locationDescription(locationDescription)
                .deviceEvidenceToken(maskedDevice)
                .windowClassification(windowClass)
                .flaggedForReview(status == CheckInStatus.FLAGGED)
                .build();
    }
}
