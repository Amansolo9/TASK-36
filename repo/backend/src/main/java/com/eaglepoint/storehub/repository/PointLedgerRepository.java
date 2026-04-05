package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    List<PointLedger> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(pl.points), 0) FROM PointLedger pl WHERE pl.user.id = :userId")
    int getTotalPointsByUserId(@Param("userId") Long userId);
}
