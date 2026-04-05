package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.UserDto;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SiteAuthorizationService siteAuth;

    @DataScope
    @Transactional(readOnly = true)
    public List<UserDto> findAllUsers() {
        List<Long> visibleSiteIds = DataScopeContext.get();
        List<User> users;

        if (visibleSiteIds == null) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findAllBySiteIdIn(visibleSiteIds);
        }

        return users.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        siteAuth.requireOwnerOrSiteAccess(id, user.getSite() != null ? user.getSite().getId() : null);
        return toDto(user);
    }

    @Audited(action = "ROLE_UPDATE", entityType = "User")
    @RequiresRecentAuth
    @Transactional
    public UserDto updateRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role oldRole = user.getRole();
        user.setRole(newRole);
        log.info("User role updated: userId={}, oldRole={}, newRole={}", userId, oldRole, newRole);
        return toDto(userRepository.save(user));
    }

    @Audited(action = "DISABLE", entityType = "User")
    @RequiresRecentAuth
    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(false);
        userRepository.save(user);
        log.info("User disabled: userId={}, username='{}'", userId, user.getUsername());
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        dto.setSiteId(user.getSite() != null ? user.getSite().getId() : null);
        dto.setSiteName(user.getSite() != null ? user.getSite().getName() : null);
        dto.setEnabled(user.isEnabled());

        // Server-side data minimization: only privileged callers see raw PII
        boolean isPrivileged = isCallerPrivileged();
        boolean isSelf = isCallerUser(user.getId());

        if (isPrivileged || isSelf) {
            dto.setEmail(user.getEmail());
            dto.setAddress(user.getAddress());
            dto.setDeviceId(user.getDeviceId());
        } else {
            // Masked values for lower-privilege callers
            dto.setEmail(maskEmail(user.getEmail()));
            dto.setAddress(user.getAddress() != null ? "****" : null);
            dto.setDeviceId(user.getDeviceId() != null ? "****" : null);
        }

        return dto;
    }

    private boolean isCallerPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            String role = p.getRole();
            return "ENTERPRISE_ADMIN".equals(role) || "SITE_MANAGER".equals(role);
        }
        return false;
    }

    private boolean isCallerUser(Long userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p.getId().equals(userId);
        }
        return false;
    }

    private String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "****" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
