package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.EvidenceDto;
import com.eaglepoint.storehub.dto.TicketRequest;
import com.eaglepoint.storehub.dto.TicketResponse;
import com.eaglepoint.storehub.enums.TicketStatus;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.EvidenceService;
import com.eaglepoint.storehub.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService ticketService;
    private final EvidenceService evidenceService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponse> createTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TicketRequest request) {
        return ResponseEntity.ok(ticketService.createTicket(principal.getId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponse> getTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ticketService.getTicket(id, principal.getId(), principal.getRole()));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TicketResponse>> getMyTickets(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ticketService.getMyTickets(principal.getId()));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','STAFF')")
    public ResponseEntity<List<TicketResponse>> getByStatus(@PathVariable TicketStatus status) {
        return ResponseEntity.ok(ticketService.getTicketsByStatus(status));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','STAFF')")
    @RequiresRecentAuth
    public ResponseEntity<TicketResponse> updateStatus(
            @PathVariable Long id, @RequestParam TicketStatus status) {
        return ResponseEntity.ok(ticketService.updateStatus(id, status));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<TicketResponse> assign(
            @PathVariable Long id, @RequestParam Long staffId) {
        return ResponseEntity.ok(ticketService.assignTicket(id, staffId));
    }

    @PostMapping("/{id}/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EvidenceDto> uploadEvidence(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(evidenceService.uploadEvidence(id, principal.getId(), file));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EvidenceDto>> getEvidence(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Reuse ticket access check
        ticketService.getTicket(id, principal.getId(), principal.getRole());
        return ResponseEntity.ok(evidenceService.getEvidenceForTicket(id));
    }

    @GetMapping("/evidence/{evidenceId}/verify")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<Map<String, Object>> verifyIntegrity(@PathVariable Long evidenceId) throws IOException {
        boolean intact = evidenceService.verifyIntegrity(evidenceId);
        return ResponseEntity.ok(Map.of("evidenceId", evidenceId, "integrityVerified", intact));
    }
}
