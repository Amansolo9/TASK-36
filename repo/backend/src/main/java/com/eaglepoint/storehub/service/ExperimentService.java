package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.dto.ExperimentDto;
import com.eaglepoint.storehub.entity.Experiment;
import com.eaglepoint.storehub.entity.ExperimentOutcome;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.ExperimentType;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.ExperimentOutcomeRepository;
import com.eaglepoint.storehub.repository.ExperimentRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Deterministic bucketing service that assigns users to experiment variants
 * using a hash of user_id + experiment_name. No external server required.
 *
 * Site-scoping: SITE_MANAGER can only create/mutate experiments scoped to their site.
 * ENTERPRISE_ADMIN can manage all experiments (global and site-scoped).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private static final double EPSILON = 0.1;

    private final ExperimentRepository experimentRepository;
    private final ExperimentOutcomeRepository outcomeRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Audited(action = "CREATE", entityType = "Experiment")
    @Transactional
    public ExperimentDto createExperiment(ExperimentDto dto) {
        if (experimentRepository.findByName(dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Experiment name already exists");
        }

        UserPrincipal principal = getCurrentPrincipal();
        Role role = Role.valueOf(principal.getRole());

        Organization site = null;
        if (role == Role.SITE_MANAGER) {
            // SITE_MANAGER must scope experiments to their own site
            Long siteId = dto.getSiteId() != null ? dto.getSiteId() : principal.getSiteId();
            if (siteId == null || !siteId.equals(principal.getSiteId())) {
                throw new AccessDeniedException(
                        "Site managers can only create experiments for their own site");
            }
            site = organizationRepository.findById(siteId)
                    .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        } else if (role == Role.ENTERPRISE_ADMIN && dto.getSiteId() != null) {
            // ENTERPRISE_ADMIN can optionally scope to a site
            site = organizationRepository.findById(dto.getSiteId())
                    .orElseThrow(() -> new IllegalArgumentException("Site not found"));
        }

        Experiment exp = Experiment.builder()
                .name(dto.getName())
                .type(dto.getType())
                .variantCount(dto.getVariantCount())
                .active(true)
                .description(dto.getDescription())
                .site(site)
                .build();

        return toDto(experimentRepository.save(exp));
    }

    @Transactional(readOnly = true)
    public List<ExperimentDto> getActiveExperiments() {
        UserPrincipal principal = getCurrentPrincipal();
        Role role = Role.valueOf(principal.getRole());

        if (role == Role.ENTERPRISE_ADMIN) {
            return experimentRepository.findByActiveTrue().stream()
                    .map(this::toDto).toList();
        }

        // Non-admin users see their site-scoped + global experiments
        Long siteId = principal.getSiteId();
        if (siteId != null) {
            return experimentRepository.findActiveBySiteOrGlobal(siteId).stream()
                    .map(this::toDto).toList();
        }
        // No site — only global experiments
        return experimentRepository.findByActiveTrue().stream()
                .filter(e -> e.getSite() == null)
                .map(this::toDto).toList();
    }

    /**
     * Deterministically assigns a user to a variant for a given experiment.
     * Uses SHA-256(userId + experimentName) mod variantCount.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBucket(Long userId, String experimentName) {
        Experiment exp = experimentRepository.findByName(experimentName)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found: " + experimentName));

        if (!exp.isActive()) {
            return Map.of("experiment", experimentName, "active", false, "variant", -1);
        }

        int variant;
        if (exp.getType() == ExperimentType.BANDIT) {
            variant = banditBucket(exp, userId);
        } else {
            variant = deterministicBucket(userId, experimentName, exp.getVariantCount());
        }

        return Map.of(
                "experiment", experimentName,
                "type", exp.getType().name(),
                "variant", variant,
                "variantCount", exp.getVariantCount(),
                "active", true
        );
    }

    @Audited(action = "UPDATE", entityType = "Experiment")
    @Transactional
    public ExperimentDto updateExperiment(Long experimentId, ExperimentDto dto) {
        Experiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        enforceExperimentAccess(exp);
        exp.setDescription(dto.getDescription());
        exp.setVariantCount(dto.getVariantCount());
        exp.setVersion(exp.getVersion() + 1); // Increment version on config change
        return toDto(experimentRepository.save(exp));
    }

    @Audited(action = "DEACTIVATE", entityType = "Experiment")
    @Transactional
    public ExperimentDto deactivate(Long experimentId) {
        Experiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        enforceExperimentAccess(exp);
        exp.setActive(false);
        exp.setVersion(exp.getVersion() + 1);
        return toDto(experimentRepository.save(exp));
    }

    @Audited(action = "ROLLBACK", entityType = "Experiment")
    @Transactional
    public ExperimentDto rollback(Long experimentId) {
        Experiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        enforceExperimentAccess(exp);
        // Rollback: reactivate and increment version to indicate reversion
        exp.setActive(true);
        exp.setVersion(exp.getVersion() + 1);
        return toDto(experimentRepository.save(exp));
    }

    /**
     * Enforces that the current user has permission to mutate an experiment.
     * ENTERPRISE_ADMIN: unrestricted.
     * SITE_MANAGER: can only mutate experiments scoped to their own site.
     */
    private void enforceExperimentAccess(Experiment exp) {
        UserPrincipal principal = getCurrentPrincipal();
        Role role = Role.valueOf(principal.getRole());

        if (role == Role.ENTERPRISE_ADMIN) {
            return; // unrestricted
        }

        if (role == Role.SITE_MANAGER) {
            Long expSiteId = exp.getSite() != null ? exp.getSite().getId() : null;
            if (expSiteId == null || !expSiteId.equals(principal.getSiteId())) {
                throw new AccessDeniedException(
                        "Site managers can only modify experiments scoped to their own site");
            }
            return;
        }

        throw new AccessDeniedException("Insufficient permissions to modify experiments");
    }

    private UserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new AccessDeniedException("Authentication required");
    }

    /**
     * Deterministic bucketing: SHA-256(userId + ":" + experimentName) mod variantCount.
     * Consistent for the same user+experiment pair, evenly distributed.
     */
    private int deterministicBucket(Long userId, String experimentName, int variantCount) {
        try {
            String input = userId + ":" + experimentName;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Use first 4 bytes as unsigned int
            int value = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);

            return Math.abs(value) % variantCount;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Deterministic epsilon-greedy bandit allocation.
     * Uses user+experiment hash to determine explore vs exploit deterministically per user.
     * Same user+experiment+version always gets the same assignment.
     */
    private int banditBucket(Experiment exp, Long userId) {
        // Deterministic exploration decision based on user hash
        int userHash = deterministicBucket(userId, exp.getName() + ":bandit", 100);
        boolean explore = userHash < (int)(EPSILON * 100); // e.g., 10% of users explore

        if (explore) {
            // Deterministic random variant for exploration
            return deterministicBucket(userId, exp.getName() + ":explore:v" + exp.getVersion(), exp.getVariantCount());
        }

        // Exploit: pick highest average reward variant
        List<Object[]> stats = outcomeRepository.getVariantStats(exp.getId());
        if (stats.isEmpty()) {
            return deterministicBucket(userId, exp.getName() + ":fallback", exp.getVariantCount());
        }

        int bestVariant = -1;
        double bestAvg = Double.NEGATIVE_INFINITY;
        for (Object[] row : stats) {
            int v = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            double totalReward = ((Number) row[2]).doubleValue();
            double avg = totalReward / count;
            if (avg > bestAvg) {
                bestAvg = avg;
                bestVariant = v;
            }
        }
        return bestVariant >= 0 ? bestVariant : deterministicBucket(userId, exp.getName(), exp.getVariantCount());
    }

    @Audited(action = "RECORD_OUTCOME", entityType = "ExperimentOutcome")
    @Transactional
    public void recordOutcome(String experimentName, Long userId, int variant, double reward) {
        Experiment exp = experimentRepository.findByName(experimentName)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        ExperimentOutcome outcome = ExperimentOutcome.builder()
                .experiment(exp).variant(variant).reward(reward).user(user).build();
        outcomeRepository.save(outcome);
    }

    private ExperimentDto toDto(Experiment exp) {
        ExperimentDto dto = new ExperimentDto();
        dto.setId(exp.getId());
        dto.setName(exp.getName());
        dto.setType(exp.getType());
        dto.setVariantCount(exp.getVariantCount());
        dto.setDescription(exp.getDescription());
        dto.setSiteId(exp.getSite() != null ? exp.getSite().getId() : null);
        dto.setActive(exp.isActive());
        dto.setVersion(exp.getVersion());
        return dto;
    }
}
