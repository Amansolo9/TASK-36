package com.eaglepoint.storehub.service;

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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Organization testSite;
    private Validator validator;

    @BeforeEach
    void setUp() {
        testSite = Organization.builder()
                .id(1L)
                .name("Test Site")
                .build();

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("encodedPassword")
                .role(Role.CUSTOMER)
                .site(testSite)
                .lastAuthenticatedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // --- Login tests ---

    @Test
    void login_success_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        UserPrincipal principal = new UserPrincipal(testUser);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("testuser", response.getUsername());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals(1L, response.getSiteId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_badCredentials_throwsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    // --- Register tests ---

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("SecureP@ss1");
        request.setSiteId(1L);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testSite));
        when(passwordEncoder.encode("SecureP@ss1")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });
        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("newuser", response.getUsername());
        assertEquals("CUSTOMER", response.getRole());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("other@example.com");
        request.setPassword("SecureP@ss1");

        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Username already taken", ex.getMessage());
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("test@example.com");
        request.setPassword("SecureP@ss1");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertEquals("Email already in use", ex.getMessage());
    }

    // --- Password validation tests (DTO level) ---

    @Test
    void passwordValidation_tooShort_fails() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("user@example.com");
        request.setPassword("Sh0rt!");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("at least 10 characters")));
    }

    @Test
    void passwordValidation_noNumber_fails() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("user@example.com");
        request.setPassword("NoNumberHere!");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("1 number and 1 symbol")));
    }

    @Test
    void passwordValidation_noSymbol_fails() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("user@example.com");
        request.setPassword("NoSymbol12345");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("1 number and 1 symbol")));
    }

    @Test
    void passwordValidation_validPassword_passes() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user");
        request.setEmail("user@example.com");
        request.setPassword("ValidP@ss10");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    // --- Reauthenticate tests ---

    @Test
    void reauthenticate_success_returnsAuthResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("new-jwt-token");

        AuthResponse response = authService.reauthenticate(1L, "password");

        assertNotNull(response);
        assertEquals("new-jwt-token", response.getToken());
        assertEquals("testuser", response.getUsername());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void reauthenticate_wrongPassword_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.reauthenticate(1L, "wrongpassword"));
        assertEquals("Invalid password", ex.getMessage());
    }
}
