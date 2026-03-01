package com.app.craftyhire.security;

import com.app.craftyhire.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — intercepts every HTTP request and validates
 * the Bearer token in the Authorization header.
 *
 * Extends OncePerRequestFilter to guarantee exactly one execution per
 * request, even if the filter appears in multiple filter chains.
 *
 * Flow:
 *   1. Extract the "Bearer <token>" from the Authorization header
 *   2. Parse the token and extract the user's email
 *   3. Load the UserDetails from the database
 *   4. Validate the token (signature, expiry, subject match)
 *   5. If valid, set authentication in the SecurityContext
 *   6. Pass the request down the filter chain
 *
 * If no token is present or the token is invalid, the request continues
 * unauthenticated. Spring Security will then enforce access rules
 * configured in SecurityConfig (returning 401 for protected endpoints).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No Authorization header or not a Bearer token — skip JWT processing
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip the "Bearer " prefix to get the raw token
        final String jwt = authHeader.substring(7);
        final String userEmail;

        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // Token is malformed or has an invalid signature — log and skip
            log.debug("Invalid JWT token: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Only set authentication if we have a username and no existing auth in context
        // (prevents overwriting authentication set by earlier filters)
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.validateToken(jwt, userDetails)) {
                // Build an authenticated token and populate it with request details
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,                           // credentials not needed after authentication
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store the authentication in the SecurityContext for this request
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user '{}' via JWT", userEmail);
            }
        }

        filterChain.doFilter(request, response);
    }
}