package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    Optional<Experiment> findByName(String name);

    List<Experiment> findByActiveTrue();

    /** Active experiments visible to a specific site (site-scoped + global). */
    @Query("SELECT e FROM Experiment e WHERE e.active = true AND (e.site IS NULL OR e.site.id = :siteId)")
    List<Experiment> findActiveBySiteOrGlobal(Long siteId);

    /** Experiments owned by a specific site. */
    List<Experiment> findBySiteId(Long siteId);
}
