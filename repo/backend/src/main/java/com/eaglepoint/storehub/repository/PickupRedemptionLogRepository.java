package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.PickupRedemptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PickupRedemptionLogRepository extends JpaRepository<PickupRedemptionLog, Long> {
    List<PickupRedemptionLog> findByOrderIdOrderByAttemptedAtDesc(Long orderId);
}
