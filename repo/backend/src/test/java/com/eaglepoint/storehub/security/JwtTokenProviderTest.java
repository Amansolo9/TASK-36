package com.eaglepoint.storehub.security;

import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private UserPrincipal testPrincipal;

    // A 256-bit key encoded in Base64 for testing
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString(
                    "ThisIsATestSecretKeyThatIs32Bytes".getBytes());
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);

        Organization site = Organization.builder()
                .id(1L)
                .name("Test Site")
                .build();

        User user = User.builder()
                .id(42L)
                .username("jwtuser")
                .email("jwt@example.com")
                .passwordHash("encoded")
                .role(Role.CUSTOMER)
                .site(site)
                .enabled(true)
                .lastAuthenticatedAt(Instant.now())
                .build();

        testPrincipal = new UserPrincipal(user);
    }

    @Test
    void generateToken_andValidate_succeeds() {
        String token = tokenProvider.generateToken(testPrincipal);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void getUserIdFromToken_returnsCorrectId() {
        String token = tokenProvider.generateToken(testPrincipal);

        Long userId = tokenProvider.getUserIdFromToken(token);

        assertEquals(42L, userId);
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        // Create a provider with 0ms expiration so the token is immediately expired
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 0);
        String token = shortLivedProvider.generateToken(testPrincipal);

        // The token was issued with expiration = issuedAt + 0ms, so it is already expired
        assertFalse(shortLivedProvider.validateToken(token));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = tokenProvider.generateToken(testPrincipal);

        // Tamper with the token by changing a character in the signature (last part)
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(tokenProvider.validateToken(tamperedToken));
    }

    @Test
    void validateToken_tokenSignedWithDifferentKey_returnsFalse() {
        // Create a token with a different secret
        String differentSecret = Base64.getEncoder().encodeToString(
                "ADifferentSecretKeyThatIs32Byte!".getBytes());
        JwtTokenProvider otherProvider = new JwtTokenProvider(differentSecret, EXPIRATION_MS);
        String token = otherProvider.generateToken(testPrincipal);

        // Validate with the original provider (different key)
        assertFalse(tokenProvider.validateToken(token));
    }
}
