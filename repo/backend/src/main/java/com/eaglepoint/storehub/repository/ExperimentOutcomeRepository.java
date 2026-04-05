package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.ExperimentOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperimentOutcomeRepository extends JpaRepository<ExperimentOutcome, Long> {

    @Query("SELECT o.variant, COUNT(o), SUM(o.reward) FROM ExperimentOutcome o WHERE o.experiment.id = :expId GROUP BY o.variant")
    List<Object[]> getVariantStats(@Param("expId") Long experimentId);
}
