package com.app.craftyhire.controller;

import com.app.craftyhire.dto.request.LoginRequest;
import com.app.craftyhire.dto.request.RefreshRequest;
import com.app.craftyhire.dto.request.RegisterRequest;
import com.app.craftyhire.dto.response.AuthResponse;
import com.app.craftyhire.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 *
 * All endpoints under /api/auth/** are public (no JWT required).
 * This is configured in SecurityConfig.
 *
 * Endpoints:
 *   POST /api/auth/register  — create a new account
 *   POST /api/auth/login     — login and receive JWT tokens
 *   POST /api/auth/logout    — invalidate the current session
 *   POST /api/auth/refresh   — exchange a refresh token for a new access token
 *
 * Scalability note: Add endpoints here as auth grows:
 *   - POST /api/auth/forgot-password
 *   - POST /api/auth/reset-password
 *   - GET  /api/auth/verify-email
 *   - POST /api/auth/oauth2/{provider}  (Google, GitHub, etc.)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user account.
     *
     * Returns 201 Created with JWT tokens on success.
     * Returns 400 Bad Request if validation fails or email is already taken.
     *
     * @param request email, password, confirmPassword
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate with email and password.
     *
     * Returns 200 OK with JWT tokens on success.
     * Returns 401 Unauthorized if credentials are invalid.
     *
     * @param request email, password
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Log out the current user.
     *
     * The client should discard stored tokens after calling this endpoint.
     * Returns 204 No Content on success.
     *
     * Note: Server-side blacklisting is not yet implemented (see AuthService).
     * Tokens will expire naturally based on their configured TTL.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // Pass the Authorization header to AuthService for logging/future blacklisting
        authService.logout(request.getHeader("Authorization"));
        return ResponseEntity.noContent().build();
    }

    /**
     * Exchange a valid refresh token for a new access token.
     *
     * Call this when the frontend receives a 401 response on a protected endpoint.
     * Returns 200 OK with a new access token (same refresh token is returned).
     * Returns 400 Bad Request if the refresh token is invalid or expired.
     *
     * @param request the refresh token string
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }
}