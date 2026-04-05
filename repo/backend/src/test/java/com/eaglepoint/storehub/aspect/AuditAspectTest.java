package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private EntityManager entityManager;

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(auditService, new ObjectMapper(), entityManager);
    }

    @Test
    void sanitize_redactsPasswords() {
        String json = "{\"username\":\"admin\",\"password\":\"secret123\"}";
        String result = auditAspect.sanitize(json);

        assertTrue(result.contains("\"password\":\"[REDACTED]\""));
        assertTrue(result.contains("\"username\":\"admin\""));
    }

    @Test
    void sanitize_redactsPasswordHash() {
        String json = "{\"passwordHash\":\"$2a$10$abc\"}";
        String result = auditAspect.sanitize(json);

        assertTrue(result.contains("\"passwordHash\":\"[REDACTED]\""));
    }

    @Test
    void sanitize_redactsDeviceFingerprint() {
        String json = "{\"deviceFingerprint\":\"abc123def456\"}";
        String result = auditAspect.sanitize(json);

        assertTrue(result.contains("\"deviceFingerprint\":\"[REDACTED]\""));
    }

    @Test
    void sanitize_masksEmails() {
        String json = "{\"email\":\"john@example.com\"}";
        String result = auditAspect.sanitize(json);

        assertTrue(result.contains("\"email\":\"jo***@example.com\""));
        assertFalse(result.contains("john@example.com"));
    }

    @Test
    void sanitize_preservesNonSensitiveFields() {
        String json = "{\"id\":1,\"name\":\"Test Order\",\"status\":\"PENDING\"}";
        String result = auditAspect.sanitize(json);

        assertEquals(json, result);
    }

    @Test
    void sanitize_handlesNull() {
        assertNull(auditAspect.sanitize(null));
    }

    @Test
    void sanitize_redactsToken() {
        String json = "{\"token\":\"eyJhbGciOiJIUzI1NiJ9.abc\"}";
        String result = auditAspect.sanitize(json);

        assertTrue(result.contains("\"token\":\"[REDACTED]\""));
    }
}
