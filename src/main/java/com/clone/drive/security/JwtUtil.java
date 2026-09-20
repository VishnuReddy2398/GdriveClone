package com.clone.drive.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;

/**
 * Utility class for handling JWT generation and validation.
 * Crucial for stateless, secure authentication.
 */
@Component
public class JwtUtil {

    private static final String DEFAULT_DEV_SECRET = "SuperSecretKeyForDevelopmentOnlyDoNotUseInProd12345!";

    // Must be at least 256 bits (32 characters) for HMAC-SHA256
    @Value("${jwt.secret:SuperSecretKeyForDevelopmentOnlyDoNotUseInProd12345!}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
    private long expiration;

    private final Environment environment;

    public JwtUtil(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecret() {
        boolean isProduction = false;
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                    isProduction = true;
                    break;
                }
            }
        }

        if (isProduction) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: 'jwt.secret' must be explicitly provided in production!");
            }
            if (DEFAULT_DEV_SECRET.equals(secret)) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: Application cannot start in production with default development JWT secret!");
            }
            if (secret.length() < 32) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: Production JWT secret must be at least 32 characters (256 bits) long!");
            }
            // Entropy check: ensure it is not trivial/repeating like "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            long distinctChars = secret.chars().distinct().count();
            if (distinctChars < 10) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: Production JWT secret lacks sufficient entropy!");
            }
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get("sessionId", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generates a token for a given UserDetails.
     */
    public String generateToken(UserDetails userDetails, String sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sessionId", sessionId);
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * Creates the JWT string with signing and expiration.
     */
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validates that the token belongs to the user and is not expired.
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
