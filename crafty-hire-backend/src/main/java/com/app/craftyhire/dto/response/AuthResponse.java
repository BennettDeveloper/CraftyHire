package com.app.craftyhire.dto.response;

import lombok.*;

/**
 * Response body returned after a successful login or registration.
 * Contains the JWT tokens needed for subsequent authenticated requests.
 *
 * The frontend should:
 *   1. Store accessToken in memory (not localStorage) for security.
 *   2. Store refreshToken in an httpOnly cookie (or secure storage).
 *   3. Include accessToken in all API requests as: Authorization: Bearer <token>
 *   4. Call /api/auth/refresh when a 401 is received to get a new access token.
 *
 * Scalability note: Add user profile fields here (displayName, avatarUrl,
 * subscriptionTier) as the user model expands.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Short-lived JWT access token (default: 15 minutes).
     * Include in the Authorization header: "Bearer <accessToken>"
     */
    private String accessToken;

    /**
     * Long-lived refresh token (default: 7 days).
     * Use to obtain a new access token without requiring re-login.
     */
    private String refreshToken;

    /** The authenticated user's email address */
    private String email;

    /**
     * The user's role, used by the frontend to conditionally show UI elements.
     * Current values: "USER", "ADMIN"
     */
    private String role;
}