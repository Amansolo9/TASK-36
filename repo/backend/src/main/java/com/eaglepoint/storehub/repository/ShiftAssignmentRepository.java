package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {
    @Query("SELECT s FROM ShiftAssignment s WHERE s.user.id = :userId AND s.site.id = :siteId AND s.shiftDate = :date AND s.active = true")
    Optional<ShiftAssignment> findActiveShift(@Param("userId") Long userId, @Param("siteId") Long siteId, @Param("date") LocalDate date);
}
