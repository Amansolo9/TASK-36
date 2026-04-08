package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.config.AccessDeniedException;
import com.eaglepoint.storehub.dto.ExperimentDto;
import com.eaglepoint.storehub.entity.Experiment;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.ExperimentType;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.ExperimentOutcomeRepository;
import com.eaglepoint.storehub.repository.ExperimentRepository;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock private ExperimentRepository experimentRepository;
    @Mock private ExperimentOutcomeRepository outcomeRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExperimentService experimentService;

    private Organization site10;
    private Organization site20;

    @BeforeEach
    void setUp() {
        site10 = Organization.builder().id(10L).name("Site A").build();
        site20 = Organization.builder().id(20L).name("Site B").build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ═══════════════════════════════════════════════════════════
    //  Create: site-scoping
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("SITE_MANAGER creates experiment scoped to own site → success")
    void siteManager_createExperiment_ownSite_succeeds() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        ExperimentDto dto = buildDto("test-exp", 10L);
        when(experimentRepository.findByName("test-exp")).thenReturn(Optional.empty());
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(site10));
        when(experimentRepository.save(any())).thenAnswer(inv -> {
            Experiment e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ExperimentDto result = experimentService.createExperiment(dto);
        assertNotNull(result);
        assertEquals(10L, result.getSiteId());
    }

    @Test
    @DisplayName("SITE_MANAGER cannot create experiment for another site → AccessDenied")
    void siteManager_createExperiment_otherSite_denied() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        ExperimentDto dto = buildDto("test-exp", 20L); // different site

        assertThrows(AccessDeniedException.class,
                () -> experimentService.createExperiment(dto));
    }

    @Test
    @DisplayName("SITE_MANAGER cannot create global experiment (null siteId) → uses own site")
    void siteManager_createExperiment_nullSite_usesOwnSite() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        ExperimentDto dto = buildDto("test-exp", null); // no explicit site
        when(experimentRepository.findByName("test-exp")).thenReturn(Optional.empty());
        when(organizationRepository.findById(10L)).thenReturn(Optional.of(site10));
        when(experimentRepository.save(any())).thenAnswer(inv -> {
            Experiment e = inv.getArgument(0);
            e.setId(1L);
            // Verify it was scoped to the manager's site
            assertNotNull(e.getSite());
            assertEquals(10L, e.getSite().getId());
            return e;
        });

        experimentService.createExperiment(dto);
        verify(experimentRepository).save(any());
    }

    @Test
    @DisplayName("ENTERPRISE_ADMIN creates global experiment (null site) → success")
    void admin_createGlobalExperiment_succeeds() {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        ExperimentDto dto = buildDto("global-exp", null);
        when(experimentRepository.findByName("global-exp")).thenReturn(Optional.empty());
        when(experimentRepository.save(any())).thenAnswer(inv -> {
            Experiment e = inv.getArgument(0);
            e.setId(1L);
            assertNull(e.getSite());
            return e;
        });

        ExperimentDto result = experimentService.createExperiment(dto);
        assertNotNull(result);
        assertNull(result.getSiteId());
    }

    // ═══════════════════════════════════════════════════════════
    //  Update/Deactivate: site-scoping
    // ═══════════════════════════════════════════════════════════

    @Test
    @DisplayName("SITE_MANAGER can update experiment scoped to own site")
    void siteManager_updateExperiment_ownSite_succeeds() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        Experiment exp = buildExperiment(1L, "exp-1", site10);
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));
        when(experimentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExperimentDto dto = new ExperimentDto();
        dto.setDescription("updated");
        dto.setVariantCount(3);

        ExperimentDto result = experimentService.updateExperiment(1L, dto);
        assertEquals("updated", result.getDescription());
    }

    @Test
    @DisplayName("SITE_MANAGER cannot update global experiment → AccessDenied")
    void siteManager_updateGlobalExperiment_denied() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        Experiment exp = buildExperiment(1L, "global-exp", null); // global
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));

        ExperimentDto dto = new ExperimentDto();
        dto.setDescription("hacked");
        dto.setVariantCount(3);

        assertThrows(AccessDeniedException.class,
                () -> experimentService.updateExperiment(1L, dto));
    }

    @Test
    @DisplayName("SITE_MANAGER cannot update experiment from another site → AccessDenied")
    void siteManager_updateOtherSiteExperiment_denied() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        Experiment exp = buildExperiment(1L, "other-exp", site20); // site 20
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));

        ExperimentDto dto = new ExperimentDto();
        dto.setDescription("hacked");
        dto.setVariantCount(3);

        assertThrows(AccessDeniedException.class,
                () -> experimentService.updateExperiment(1L, dto));
    }

    @Test
    @DisplayName("ENTERPRISE_ADMIN can update any experiment (global or site-scoped)")
    void admin_updateAnyExperiment_succeeds() {
        setPrincipal(1L, Role.ENTERPRISE_ADMIN, null);
        Experiment exp = buildExperiment(1L, "any-exp", site20);
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));
        when(experimentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExperimentDto dto = new ExperimentDto();
        dto.setDescription("admin update");
        dto.setVariantCount(4);

        ExperimentDto result = experimentService.updateExperiment(1L, dto);
        assertEquals("admin update", result.getDescription());
    }

    @Test
    @DisplayName("SITE_MANAGER cannot deactivate global experiment → AccessDenied")
    void siteManager_deactivateGlobalExperiment_denied() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        Experiment exp = buildExperiment(1L, "global", null);
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));

        assertThrows(AccessDeniedException.class,
                () -> experimentService.deactivate(1L));
    }

    @Test
    @DisplayName("SITE_MANAGER can deactivate own-site experiment")
    void siteManager_deactivateOwnSite_succeeds() {
        setPrincipal(1L, Role.SITE_MANAGER, 10L);
        Experiment exp = buildExperiment(1L, "my-exp", site10);
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));
        when(experimentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExperimentDto result = experimentService.deactivate(1L);
        assertFalse(result.isActive());
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private void setPrincipal(Long userId, Role role, Long siteId) {
        User.UserBuilder builder = User.builder()
                .id(userId).username("test").email("t@t.com")
                .passwordHash("x").role(role).enabled(true).tokenVersion(0);
        if (siteId != null) {
            builder.site(Organization.builder().id(siteId).name("Site").build());
        }
        UserPrincipal principal = new UserPrincipal(builder.build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ExperimentDto buildDto(String name, Long siteId) {
        ExperimentDto dto = new ExperimentDto();
        dto.setName(name);
        dto.setType(ExperimentType.AB_TEST);
        dto.setVariantCount(2);
        dto.setDescription("test experiment");
        dto.setSiteId(siteId);
        return dto;
    }

    private Experiment buildExperiment(Long id, String name, Organization site) {
        return Experiment.builder()
                .id(id).name(name).type(ExperimentType.AB_TEST)
                .variantCount(2).active(true).version(1).site(site)
                .build();
    }
}
