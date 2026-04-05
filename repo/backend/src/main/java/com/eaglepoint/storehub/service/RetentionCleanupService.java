package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.EvidenceFile;
import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.repository.EvidenceFileRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionCleanupService {

    private final SupportTicketRepository ticketRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTickets() {
        List<SupportTicket> expired = ticketRepository.findExpiredRetentionTickets(Instant.now());

        for (SupportTicket ticket : expired) {
            List<EvidenceFile> files = evidenceFileRepository.findByTicketId(ticket.getId());

            // Audit before deletion — forensic record of what was deleted
            auditService.logSystemAction("RETENTION_DELETE", "SupportTicket", ticket.getId(),
                    "Retention cleanup: ticket deleted after 24-month retention. Evidence files: " + files.size());

            for (EvidenceFile file : files) {
                auditService.logSystemAction("RETENTION_DELETE", "EvidenceFile", file.getId(),
                        "Evidence file deleted: " + file.getFileName() + " (hash: " + file.getSha256Hash() + ")");
                try {
                    Path path = Paths.get(file.getStoragePath());
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.error("Failed to delete evidence file: {}", file.getStoragePath(), e);
                }
            }
            evidenceFileRepository.deleteAll(files);
            ticketRepository.delete(ticket);

            log.info("Retention cleanup: deleted ticket={} and {} evidence files",
                    ticket.getId(), files.size());
        }
    }
}
