package com.app.craftyhire.service;

import com.app.craftyhire.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles all JWT (JSON Web Token) operations:
 *   - Generating access tokens (short-lived, for API authorization)
 *   - Generating refresh tokens (long-lived, for renewing access tokens)
 *   - Parsing and validating tokens
 *
 * Uses HMAC-SHA256 (HS256) signing. The secret key is loaded from
 * application.properties and should be overridden with a strong random
 * secret via the JWT_SECRET environment variable in production.
 *
 * Token structure (standard JWT claims):
 *   - sub:  the user's email address
 *   - iat:  issued-at timestamp
 *   - exp:  expiration timestamp
 *   - type: "ACCESS" or "REFRESH" (custom claim to prevent token misuse)
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    // ── Token Generation ─────────────────────────────────────────────────────

    /**
     * Generates a short-lived access token for a user.
     * Include this in the Authorization header: "Bearer <token>"
     */
    public String generateAccessToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", "ACCESS");
        extraClaims.put("role", user.getRole());
        return buildToken(extraClaims, user.getEmail(), accessTokenExpiration);
    }

    /**
     * Generates a long-lived refresh token for a user.
     * Used only to obtain a new access token — not for API calls.
     */
    public String generateRefreshToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", "REFRESH");
        return buildToken(extraClaims, user.getEmail(), refreshTokenExpiration);
    }

    /**
     * Core token builder. Sets standard claims (sub, iat, exp) plus any
     * extra claims provided (e.g., type, role).
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token Validation ─────────────────────────────────────────────────────

    /**
     * Returns true if the token is valid for the given user:
     *   - The subject (email) matches the UserDetails username
     *   - The token has not expired
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Returns true if the token is a valid refresh token (not expired, correct type).
     * Used in AuthService.refreshToken() to prevent access tokens being used as refresh tokens.
     */
    public boolean isRefreshToken(String token) {
        String type = extractClaim(token, claims -> claims.get("type", String.class));
        return "REFRESH".equals(type) && !isTokenExpired(token);
    }

    // ── Claim Extraction ─────────────────────────────────────────────────────

    /** Extracts the subject (email address) from the token */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Checks whether the token's expiration timestamp is in the past */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor. Parses the token and applies the provided
     * function to the claims payload.
     *
     * @param token          the JWT string
     * @param claimsResolver a function that maps Claims → T
     */
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

    // ── Key ──────────────────────────────────────────────────────────────────

    /**
     * Builds the HMAC-SHA256 signing key from the configured secret string.
     * The secret must be at least 32 characters (256 bits) for HS256.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}