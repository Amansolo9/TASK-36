package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.dto.CreditScoreDto;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.CreditScoreService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credit-score")
@RequiredArgsConstructor
public class CreditScoreController {

    private final CreditScoreService creditScoreService;
    private final SiteAuthorizationService siteAuth;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<CreditScoreDto> getMyScore(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(creditScoreService.getScore(principal.getId()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<CreditScoreDto> getUserScore(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Long userSiteId = user.getSite() != null ? user.getSite().getId() : null;
        siteAuth.requireOwnerOrSiteAccess(userId, userSiteId);
        return ResponseEntity.ok(creditScoreService.getScore(userId));
    }
}
