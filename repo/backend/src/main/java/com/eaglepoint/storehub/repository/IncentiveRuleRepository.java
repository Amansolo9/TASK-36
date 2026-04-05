package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.IncentiveRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncentiveRuleRepository extends JpaRepository<IncentiveRule, Long> {

    Optional<IncentiveRule> findByActionKey(String actionKey);

    List<IncentiveRule> findByActiveTrue();
}
