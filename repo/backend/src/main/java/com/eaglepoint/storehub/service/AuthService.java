package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.AuthResponse;
import com.eaglepoint.storehub.dto.LoginRequest;
import com.eaglepoint.storehub.dto.RegisterRequest;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.security.JwtTokenProvider;
import com.eaglepoint.storehub.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Audited(action = "LOGIN", entityType = "User")
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Check if account is locked
        User preAuthUser = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (preAuthUser != null && preAuthUser.getLockedUntil() != null
                && preAuthUser.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Account is temporarily locked. Try again later.");
        }

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException ex) {
            if (preAuthUser != null) {
                preAuthUser.setFailedLoginAttempts(preAuthUser.getFailedLoginAttempts() + 1);
                if (preAuthUser.getFailedLoginAttempts() >= 5) {
                    preAuthUser.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));
                }
                userRepository.save(preAuthUser);
            }
            throw ex;
        }

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        // Update last authenticated timestamp and reset failed login attempts
        User user = userRepository.findById(principal.getId()).orElseThrow();
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastAuthenticatedAt(Instant.now());
        user.setLastActivityAt(Instant.now());
        userRepository.save(user);

        // Re-create principal with updated auth time
        UserPrincipal updatedPrincipal = new UserPrincipal(user);
        String token = tokenProvider.generateToken(updatedPrincipal);

        log.info("Successful login for user '{}' (id={})", principal.getUsername(), principal.getId());
        return new AuthResponse(token, principal.getUsername(), principal.getRole(), principal.getSiteId());
    }

    @Audited(action = "REGISTER", entityType = "User")
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        Organization site = null;
        if (request.getSiteId() != null) {
            site = organizationRepository.findById(request.getSiteId()).orElse(null);
        }

        // M3: Public registration always assigns CUSTOMER role; elevated roles require admin action
        Role role = Role.CUSTOMER;

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .site(site)
                .lastAuthenticatedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();

        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = tokenProvider.generateToken(principal);

        log.info("New user registered: '{}' with role {} (id={})", user.getUsername(), role, user.getId());
        return new AuthResponse(token, user.getUsername(), role.name(),
                site != null ? site.getId() : null);
    }

    @Audited(action = "REAUTH", entityType = "User")
    @Transactional
    public AuthResponse reauthenticate(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Failed reauthentication attempt for userId={}", userId);
            throw new IllegalArgumentException("Invalid password");
        }

        user.setLastAuthenticatedAt(Instant.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = tokenProvider.generateToken(principal);

        return new AuthResponse(token, user.getUsername(), user.getRole().name(),
                user.getSite() != null ? user.getSite().getId() : null);
    }
}
