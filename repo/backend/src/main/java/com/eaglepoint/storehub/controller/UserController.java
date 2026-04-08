package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.AuthResponse;
import com.eaglepoint.storehub.dto.ReauthRequest;
import com.eaglepoint.storehub.dto.UserDto;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.AuthService;
import com.eaglepoint.storehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN', 'SITE_MANAGER')")
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.findById(principal.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN', 'SITE_MANAGER')")
    public ResponseEntity<UserDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<UserDto> updateRole(@PathVariable Long id, @RequestParam Role role) {
        return ResponseEntity.ok(userService.updateRole(id, role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ENTERPRISE_ADMIN')")
    @RequiresRecentAuth
    public ResponseEntity<Void> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reauth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthResponse> reauthenticate(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReauthRequest request) {
        return ResponseEntity.ok(authService.reauthenticate(principal.getId(), request.getPassword()));
    }
}
