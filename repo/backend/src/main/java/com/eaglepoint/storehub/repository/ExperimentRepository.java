package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    Optional<Experiment> findByName(String name);

    List<Experiment> findByActiveTrue();
}
