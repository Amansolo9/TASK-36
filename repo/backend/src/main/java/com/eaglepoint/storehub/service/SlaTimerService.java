package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.enums.TicketStatus;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlaTimerService {

    private final SupportTicketRepository ticketRepository;
    private final AuditService auditService;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void checkOverdueTickets() {
        List<SupportTicket> overdue = ticketRepository.findOverdueSlaTickets(Instant.now());

        for (SupportTicket ticket : overdue) {
            if (!ticket.isSlaBreached()) {
                ticket.setSlaBreached(true);
                ticket.setStatus(TicketStatus.ESCALATED);
                ticketRepository.save(ticket);

                // System-actor audit for scheduler-driven write
                auditService.logSystemAction("SLA_BREACH", "SupportTicket", ticket.getId(),
                        "SLA breached and escalated by system scheduler");

                log.warn("SLA breach: ticket={}, customer={}, created={}",
                        ticket.getId(), ticket.getCustomer().getId(), ticket.getCreatedAt());
            }
        }
    }
}
