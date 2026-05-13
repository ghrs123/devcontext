package com.fitvision.infrastructure.security;

import com.fitvision.domain.store.StoreRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final String secret;
    private final long expirationHours;

    private SecretKey signingKey;
    private long expirationSeconds;

    public JwtService(@Value("${fitvision.jwt.secret}") String secret,
                      @Value("${fitvision.jwt.expiration-hours:24}") long expirationHours) {
        this.secret = secret;
        this.expirationHours = expirationHours;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("fitvision.jwt.secret must be at least 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationHours * 3600L;
    }

    public String generateToken(UUID storeId, String email) {
        return generateToken(storeId, email, StoreRole.STORE.name());
    }

    public String generateToken(UUID storeId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(storeId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractStoreId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        if (role == null || role.isBlank()) {
            return StoreRole.STORE.name();
        }
        return role;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
