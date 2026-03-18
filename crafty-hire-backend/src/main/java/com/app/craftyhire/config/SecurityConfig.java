package com.app.craftyhire.config;

import com.app.craftyhire.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration for the CraftyHire API.
 *
 * Key design decisions:
 *   - Stateless sessions (no server-side session state) — every request must carry a JWT
 *   - CSRF disabled — not needed for stateless REST APIs (CSRF attacks require session cookies)
 *   - JWT filter runs before Spring's default UsernamePasswordAuthenticationFilter
 *
 * Public endpoints (no token required):
 *   - /api/auth/**    — login, register, refresh token
 *   - /h2-console/**  — H2 database console (development only; disable in production)
 *
 * All other endpoints require a valid JWT access token.
 *
 * Scalability note: @EnableMethodSecurity enables @PreAuthorize on individual
 * controller methods (e.g., @PreAuthorize("hasRole('ADMIN')")) for fine-grained
 * access control without changing this central config.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless JWT-based REST APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Apply CORS rules defined in CorsConfig
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // Stateless: Spring Security will not create or use HTTP sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define which endpoints are public vs. protected
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints are always public
                        .requestMatchers("/api/auth/**").permitAll()
                        // H2 console for development — remove or restrict in production
                        .requestMatchers("/h2-console/**").permitAll()
                        // Allow CORS preflight requests through before JWT auth runs
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Every other endpoint requires authentication
                        .anyRequest().authenticated()
                )

                // Return 401 (not Spring's default 403) for unauthenticated requests.
                // This is the correct REST API behavior and also triggers the frontend's
                // auto token-refresh logic in apiFetch.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )

                // Allow H2 console to render in an iframe (it uses frames internally)
                // Remove this in production if H2 console is disabled
                .headers(headers ->
                        headers.frameOptions(frame -> frame.disable()))

                // Register our custom authentication provider (DAO + BCrypt)
                .authenticationProvider(authenticationProvider)

                // Run JWT filter before Spring's default username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}