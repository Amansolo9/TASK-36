package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.entity.IncentiveRule;
import com.eaglepoint.storehub.enums.PointAction;
import com.eaglepoint.storehub.repository.IncentiveRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncentiveRuleService {

    private final IncentiveRuleRepository incentiveRuleRepository;

    @Transactional(readOnly = true)
    public int getPoints(String actionKey) {
        return incentiveRuleRepository.findByActionKey(actionKey)
                .filter(IncentiveRule::isActive)
                .map(IncentiveRule::getPoints)
                .orElseGet(() -> {
                    try {
                        return PointAction.valueOf(actionKey).getPoints();
                    } catch (IllegalArgumentException e) {
                        log.warn("No incentive rule or PointAction found for actionKey={}, defaulting to 0", actionKey);
                        return 0;
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<IncentiveRule> getAllRules() {
        return incentiveRuleRepository.findAll();
    }

    @Audited(action = "UPDATE", entityType = "IncentiveRule")
    @Transactional
    public IncentiveRule updateRule(String actionKey, int points) {
        IncentiveRule rule = incentiveRuleRepository.findByActionKey(actionKey)
                .orElseThrow(() -> new IllegalArgumentException("Incentive rule not found: " + actionKey));
        rule.setPoints(points);
        incentiveRuleRepository.save(rule);
        log.info("Incentive rule updated: actionKey={}, points={}", actionKey, points);
        return rule;
    }

    @Audited(action = "TOGGLE", entityType = "IncentiveRule")
    @Transactional
    public IncentiveRule toggleRule(String actionKey, boolean active) {
        IncentiveRule rule = incentiveRuleRepository.findByActionKey(actionKey)
                .orElseThrow(() -> new IllegalArgumentException("Incentive rule not found: " + actionKey));
        rule.setActive(active);
        incentiveRuleRepository.save(rule);
        log.info("Incentive rule toggled: actionKey={}, active={}", actionKey, active);
        return rule;
    }
}
