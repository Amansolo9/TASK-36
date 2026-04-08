package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.RatingRequest;
import com.eaglepoint.storehub.dto.RatingResponse;
import com.eaglepoint.storehub.enums.AppealStatus;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RatingResponse> submitRating(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.ok(ratingService.submitRating(principal.getId(), request));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RatingResponse>> getUserRatings(@PathVariable Long userId) {
        return ResponseEntity.ok(ratingService.getRatingsForUser(userId));
    }

    @GetMapping("/user/{userId}/average")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getAverageRating(@PathVariable Long userId) {
        Double avg = ratingService.getAverageRating(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "averageStars", avg != null ? avg : 0.0));
    }

    @PostMapping("/{id}/appeal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RatingResponse> submitAppeal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String reason) {
        return ResponseEntity.ok(ratingService.submitAppeal(id, principal.getId(), reason));
    }

    @PatchMapping("/{id}/appeal/resolve")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<RatingResponse> resolveAppeal(
            @PathVariable Long id,
            @RequestParam AppealStatus resolution,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ratingService.resolveAppeal(id, resolution, principal.getId(), notes));
    }

    @GetMapping("/appeals/pending")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<RatingResponse>> getPendingAppeals() {
        return ResponseEntity.ok(ratingService.getPendingAppeals());
    }
}
