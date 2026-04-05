package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    List<Order> findBySiteIdAndStatusOrderByCreatedAtDesc(Long siteId, OrderStatus status);

    List<Order> findBySiteIdOrderByCreatedAtDesc(Long siteId);

    Page<Order> findBySiteIdOrderByCreatedAtDesc(Long siteId, Pageable pageable);

    Optional<Order> findByIdAndPickupVerificationCode(Long id, String code);

    @org.springframework.data.jpa.repository.Query(
        "SELECT o FROM Order o WHERE o.site.id = :siteId AND (o.assignedStaff IS NULL OR o.assignedStaff.id = :staffId) ORDER BY o.createdAt DESC")
    Page<Order> findBySiteIdScopedToStaff(
        @org.springframework.data.repository.query.Param("siteId") Long siteId,
        @org.springframework.data.repository.query.Param("staffId") Long staffId,
        Pageable pageable);
}
