package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.CreditScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditScoreRepository extends JpaRepository<CreditScore, Long> {

    Optional<CreditScore> findByUserId(Long userId);
}
