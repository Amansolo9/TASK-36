package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SupportTicket> findByStatusOrderByCreatedAtAsc(TicketStatus status);

    @Query("SELECT t FROM SupportTicket t WHERE t.firstResponseAt IS NULL AND t.firstResponseDueAt < :now")
    List<SupportTicket> findOverdueSlaTickets(@Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.customer.id = :customerId AND t.status IN ('APPROVED','REFUNDED')")
    long countPriorApprovedTickets(@Param("customerId") Long customerId);

    @Query("SELECT t FROM SupportTicket t WHERE t.retentionExpiresAt < :now AND t.status = 'CLOSED'")
    List<SupportTicket> findExpiredRetentionTickets(@Param("now") Instant now);
}
