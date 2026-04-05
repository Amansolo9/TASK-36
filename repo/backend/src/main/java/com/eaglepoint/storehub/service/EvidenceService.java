package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.EvidenceDto;
import com.eaglepoint.storehub.entity.EvidenceFile;
import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.EvidenceFileRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/jpg"
    );

    private final EvidenceFileRepository evidenceFileRepository;
    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SiteAuthorizationService siteAuth;

    @Value("${app.evidence.storage-path:./evidence-storage}")
    private String storagePath;

    @Audited(action = "UPLOAD", entityType = "Evidence")
    @Transactional
    public EvidenceDto uploadEvidence(Long ticketId, Long uploaderId, MultipartFile file) throws IOException {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Site-scoped authorization — owner or site staff
        siteAuth.requireOwnerOrSiteAccess(ticket.getCustomer().getId(), ticket.getOrder().getSite().getId());

        // Validate file
        if (file.isEmpty()) {
            log.warn("Evidence upload validation failed: empty file for ticketId={}", ticketId);
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("Evidence upload validation failed: file exceeds 10MB limit for ticketId={}, size={}", ticketId, file.getSize());
            throw new IllegalArgumentException("File exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            log.warn("Evidence upload validation failed: disallowed content type '{}' for ticketId={}", contentType, ticketId);
            throw new IllegalArgumentException("Only PDF and JPG/PNG files are allowed");
        }

        // Compute SHA-256 hash for tamper detection
        byte[] fileBytes = file.getBytes();
        String sha256 = computeSha256(fileBytes);

        // Store file locally with safe server-generated filename
        Path dir = Paths.get(storagePath, ticketId.toString()).normalize();
        Files.createDirectories(dir);

        // Extract only the file extension from original name, not the full name
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.')).replaceAll("[^.a-zA-Z0-9]", "");
        }
        String storedName = UUID.randomUUID() + ext;
        Path filePath = dir.resolve(storedName).normalize();

        // Ensure the resolved path stays within the storage directory
        if (!filePath.startsWith(dir)) {
            throw new IllegalArgumentException("Invalid file path: path traversal detected");
        }
        Files.write(filePath, fileBytes);

        EvidenceFile evidence = EvidenceFile.builder()
                .ticket(ticket)
                .uploadedBy(uploader)
                .fileName(file.getOriginalFilename())
                .contentType(contentType)
                .fileSize(file.getSize())
                .storagePath(filePath.toString())
                .sha256Hash(sha256)
                .build();

        evidence = evidenceFileRepository.save(evidence);
        log.info("Evidence uploaded: evidenceId={}, ticketId={}, fileName='{}', sha256={}", evidence.getId(), ticketId, file.getOriginalFilename(), sha256);
        return toDto(evidence);
    }

    @Transactional(readOnly = true)
    public List<EvidenceDto> getEvidenceForTicket(Long ticketId) {
        return evidenceFileRepository.findByTicketId(ticketId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public boolean verifyIntegrity(Long evidenceId) throws IOException {
        EvidenceFile evidence = evidenceFileRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found"));

        SupportTicket ticket = evidence.getTicket();
        siteAuth.requireSiteAccess(ticket.getOrder().getSite().getId());

        byte[] fileBytes = Files.readAllBytes(Paths.get(evidence.getStoragePath()));
        String currentHash = computeSha256(fileBytes);

        return currentHash.equals(evidence.getSha256Hash());
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private EvidenceDto toDto(EvidenceFile f) {
        EvidenceDto d = new EvidenceDto();
        d.setId(f.getId());
        d.setFileName(f.getFileName());
        d.setContentType(f.getContentType());
        d.setFileSize(f.getFileSize());
        d.setSha256Hash(f.getSha256Hash());
        d.setCreatedAt(f.getCreatedAt());
        return d;
    }
}
