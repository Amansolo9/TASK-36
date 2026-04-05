package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.OrderRequest;
import com.eaglepoint.storehub.dto.OrderResponse;
import com.eaglepoint.storehub.enums.OrderStatus;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.OrderService;
import com.eaglepoint.storehub.service.ShippingLabelService;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ShippingLabelService shippingLabelService;
    private final SiteAuthorizationService siteAuth;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(principal.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.getOrder(id, principal.getId(), principal.getRole()));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(principal.getId(), pageable));
    }

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<Page<OrderResponse>> getOrdersBySite(
            @PathVariable Long siteId, Pageable pageable) {
        siteAuth.requireSiteAccess(siteId);
        return ResponseEntity.ok(orderService.getOrdersBySite(siteId, pageable));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    @RequiresRecentAuth
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }

    @PostMapping("/{id}/verify-pickup")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<OrderResponse> verifyPickup(
            @PathVariable Long id,
            @RequestParam String code,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.verifyPickup(id, code, principal.getId(), principal.getRole()));
    }

    @GetMapping("/{id}/shipping-label")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER','TEAM_LEAD','STAFF')")
    public ResponseEntity<byte[]> getShippingLabel(@PathVariable Long id) {
        byte[] label = shippingLabelService.generateLabel(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=label-order-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(label);
    }
}
