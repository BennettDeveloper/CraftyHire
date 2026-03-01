package com.app.craftyhire.service;

import com.app.craftyhire.dto.request.LoginRequest;
import com.app.craftyhire.dto.request.RefreshRequest;
import com.app.craftyhire.dto.request.RegisterRequest;
import com.app.craftyhire.dto.response.AuthResponse;
import com.app.craftyhire.model.User;
import com.app.craftyhire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for user authentication and token management.
 *
 * Handles:
 *   - Registration: validate input, hash password, persist user, issue tokens
 *   - Login: delegate credential check to Spring Security, issue tokens
 *   - Logout: client-side token disposal (see note on stateless logout below)
 *   - Token refresh: validate refresh token, issue new access token
 *
 * Scalability note on logout:
 *   True server-side logout with stateless JWTs requires a token blacklist
 *   (typically stored in Redis). For the demo, logout is client-side only —
 *   the client discards the tokens and they naturally expire.
 *   To add a blacklist: inject a Redis/cache service and check it in
 *   JwtAuthenticationFilter before processing the token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new user account.
     *
     * Steps:
     *   1. Validate passwords match
     *   2. Check email is not already registered
     *   3. Hash the password and persist the user
     *   4. Generate and return JWT tokens
     *
     * @param request registration details (email, password, confirmPassword)
     * @return AuthResponse containing access token, refresh token, email, and role
     * @throws IllegalArgumentException if passwords don't match or email is taken
     */
    public AuthResponse register(RegisterRequest request) {
        // Validate that both password fields match before doing any DB work
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Prevent duplicate accounts
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        // Build the user entity — password is hashed, never stored as plain text
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build(); // role defaults to "USER" via @Builder.Default in User

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return buildAuthResponse(user);
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * Delegates the actual credential check to Spring Security's
     * AuthenticationManager, which uses our DaoAuthenticationProvider
     * (BCrypt password comparison). An exception is thrown automatically
     * if credentials are invalid — we don't need to check manually.
     *
     * @param request login credentials (email, password)
     * @return AuthResponse containing access token, refresh token, email, and role
     */
    public AuthResponse login(LoginRequest request) {
        // This throws BadCredentialsException if email/password are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Credentials are valid — load the full user entity to build the response
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found after authentication"));

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Invalidates the user's session on the client side.
     *
     * Currently a no-op on the server — the client is responsible for
     * discarding the stored tokens. Tokens will expire naturally based on
     * their configured TTL.
     *
     * TODO (production): Add the token to a Redis blacklist here so it
     * cannot be used even before expiration.
     *
     * @param authorizationHeader the raw "Bearer <token>" header value (may be null)
     */
    public void logout(String authorizationHeader) {
        // Log the logout event for auditing purposes
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            String email = jwtService.extractUsername(token);
            log.info("User logged out: {}", email);
        }
        // TODO: add token to blacklist (Redis) for immediate invalidation in production
    }

    /**
     * Issues a new access token using a valid refresh token.
     *
     * The refresh token must not be expired and must be of type "REFRESH"
     * (not an access token). If valid, a new access token is issued without
     * requiring the user to log in again.
     *
     * @param request contains the refresh token string
     * @return AuthResponse with a new access token (and the same refresh token)
     * @throws IllegalArgumentException if the refresh token is invalid or expired
     */
    public AuthResponse refreshToken(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        // Ensure this is actually a refresh token, not an access token being misused
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Generate a fresh access token, but reuse the same refresh token
        String newAccessToken = jwtService.generateAccessToken(user);

        log.debug("Access token refreshed for: {}", email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // return the same refresh token
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /** Generates both tokens and builds the standard auth response for a user */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}