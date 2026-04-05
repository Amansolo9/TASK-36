package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @InjectMocks AdminBootstrapService bootstrapService;

    @Test
    void bootstrap_createsAdmin_whenNoAdminExists() {
        ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", "admin");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "Str0ng!Pass1");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapEmail", "admin@test.com");

        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        bootstrapService.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.ENTERPRISE_ADMIN, captor.getValue().getRole());
        verify(auditService).logSystemAction(eq("BOOTSTRAP_ADMIN"), eq("User"), any(), anyString());
    }

    @Test
    void bootstrap_skipped_whenAdminAlreadyExists() {
        User existingAdmin = User.builder().id(1L).username("boss").role(Role.ENTERPRISE_ADMIN).build();
        when(userRepository.findAll()).thenReturn(List.of(existingAdmin));

        bootstrapService.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrap_skipped_whenEnvVarsNotSet() {
        when(userRepository.findAll()).thenReturn(List.of());

        bootstrapService.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrap_rejected_whenPasswordTooShort() {
        ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", "admin");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "short");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapEmail", "admin@test.com");

        when(userRepository.findAll()).thenReturn(List.of());

        bootstrapService.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrap_rejected_whenPasswordLacksNumber() {
        ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", "admin");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "LongPassword!");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapEmail", "admin@test.com");

        when(userRepository.findAll()).thenReturn(List.of());

        bootstrapService.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void bootstrap_rejected_whenPasswordLacksSymbol() {
        ReflectionTestUtils.setField(bootstrapService, "bootstrapUsername", "admin");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapPassword", "LongPassword123");
        ReflectionTestUtils.setField(bootstrapService, "bootstrapEmail", "admin@test.com");

        when(userRepository.findAll()).thenReturn(List.of());

        bootstrapService.run();

        verify(userRepository, never()).save(any());
    }
}
