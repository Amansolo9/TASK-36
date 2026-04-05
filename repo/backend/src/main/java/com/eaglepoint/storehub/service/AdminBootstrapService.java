package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${app.bootstrap.admin-username:#{null}}")
    private String bootstrapUsername;

    @Value("${app.bootstrap.admin-password:#{null}}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.admin-email:#{null}}")
    private String bootstrapEmail;

    @Override
    public void run(String... args) {
        // Only bootstrap if no ENTERPRISE_ADMIN exists
        List<User> existingAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ENTERPRISE_ADMIN)
                .toList();

        if (!existingAdmins.isEmpty()) {
            log.info("Admin bootstrap: skipped — {} admin(s) already exist", existingAdmins.size());
            return;
        }

        if (bootstrapUsername == null || bootstrapPassword == null || bootstrapEmail == null) {
            log.warn("Admin bootstrap: skipped — BOOTSTRAP_ADMIN_USERNAME/PASSWORD/EMAIL not configured. " +
                    "Set these environment variables to create the first admin on a fresh deployment.");
            return;
        }

        // Same password policy as registration: min 10 chars, at least 1 number, at least 1 symbol
        if (bootstrapPassword.length() < 10
                || !bootstrapPassword.matches(".*[0-9].*")
                || !bootstrapPassword.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            log.error("Admin bootstrap: FAILED — password must be at least 10 characters with 1 number and 1 symbol");
            return;
        }

        if (userRepository.existsByUsername(bootstrapUsername)) {
            log.warn("Admin bootstrap: skipped — username '{}' already exists", bootstrapUsername);
            return;
        }

        User admin = User.builder()
                .username(bootstrapUsername)
                .email(bootstrapEmail)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ENTERPRISE_ADMIN)
                .enabled(true)
                .build();

        userRepository.save(admin);
        auditService.logSystemAction("BOOTSTRAP_ADMIN", "User", admin.getId(),
                "Initial admin '" + bootstrapUsername + "' created via bootstrap");
        log.info("Admin bootstrap: initial ENTERPRISE_ADMIN '{}' created successfully. " +
                "Remove bootstrap env vars from production config after first login.", bootstrapUsername);
    }
}
