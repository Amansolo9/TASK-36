package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Around("@annotation(audited)")
    public Object auditAction(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        // Capture true pre-image: try to load entity from DB before mutation
        String beforeState = null;
        Long preEntityId = null;
        try {
            preEntityId = extractEntityIdFromArgs(joinPoint.getArgs());
            if (preEntityId != null) {
                Class<?> entityClass = resolveEntityClass(audited.entityType());
                if (entityClass != null) {
                    Object preImage = entityManager.find(entityClass, preEntityId);
                    if (preImage != null) {
                        entityManager.detach(preImage); // detach so we get snapshot, not live reference
                        beforeState = sanitize(objectMapper.writeValueAsString(preImage));
                    }
                }
            }
            // Fallback: serialize method args if no entity found
            if (beforeState == null) {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    beforeState = sanitize(objectMapper.writeValueAsString(args));
                }
            }
        } catch (Exception e) {
            log.debug("Pre-image capture failed for {}: {}", joinPoint.getSignature().toShortString(), e.getMessage());
            // Fallback to args
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    beforeState = sanitize(objectMapper.writeValueAsString(args));
                }
            } catch (Exception ignored) {}
        }

        Object result = joinPoint.proceed();

        try {
            Long entityId = extractEntityId(result);
            if (entityId == null) entityId = preEntityId;
            String afterState = result != null ? sanitize(objectMapper.writeValueAsString(result)) : null;
            auditService.logAction(audited.action(), audited.entityType(), entityId, beforeState, afterState);
        } catch (Exception e) {
            log.error("Audit aspect failed for method {}: {}",
                    joinPoint.getSignature().toShortString(), e.getMessage(), e);
        }

        return result;
    }

    /**
     * Extract entity ID from method arguments — looks for the first Long parameter.
     */
    private Long extractEntityIdFromArgs(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Long l) return l;
        }
        return null;
    }

    /**
     * Resolve JPA entity class from entityType name used in @Audited annotation.
     */
    private Class<?> resolveEntityClass(String entityType) {
        String fqn = "com.eaglepoint.storehub.entity." + entityType;
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Long extractEntityId(Object result) {
        if (result == null) return null;
        try {
            Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            if (id instanceof Long longId) return longId;
            if (id instanceof Number number) return number.longValue();
        } catch (NoSuchMethodException e) {
            log.debug("Return type {} does not have getId() method", result.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("Failed to extract entity ID from {}: {}", result.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    String sanitize(String json) {
        if (json == null) return null;
        return json
                .replaceAll("\"(password|passwordHash|secret|aesKey|jwtSecret|token|" +
                            "deviceFingerprint|deviceId|address|street|ssn|creditCard|scoreEncrypted)\"\\s*:\\s*\"[^\"]*\"",
                            "\"$1\":\"[REDACTED]\"")
                .replaceAll("\"(email)\"\\s*:\\s*\"([^\"]{0,2})[^\"]*(@[^\"]*)\"",
                            "\"$1\":\"$2***$3\"");
    }
}
