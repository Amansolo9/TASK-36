package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);

    // Blocked mutation methods — audit log is append-only.
    // JpaRepository.save() is still available for inserts only (enforced by DB trigger).

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("Audit log records cannot be deleted");
    }

    @Override
    default void delete(AuditLog entity) {
        throw new UnsupportedOperationException("Audit log records cannot be deleted");
    }

    @Override
    default void deleteAll() {
        throw new UnsupportedOperationException("Audit log records cannot be deleted");
    }

    @Override
    default void deleteAll(Iterable<? extends AuditLog> entities) {
        throw new UnsupportedOperationException("Audit log records cannot be deleted");
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new UnsupportedOperationException("Audit log records cannot be deleted");
    }
}
