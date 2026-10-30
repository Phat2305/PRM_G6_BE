package com.example.art_course.auth;

import com.example.art_course.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtExpirationTest {
    @Test
    void tokenExpiresAfterExactly24Hours() {
        String secret = "test-secret-at-least-thirty-two-bytes-long";
        String token = new JwtTokenProvider(secret, 86_400_000)
                .generateToken(UUID.randomUUID(), "parent", "PARENT");
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals(86_400, claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond());
    }
}
