package com.app.craftyhire.config;

import com.app.craftyhire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Core application beans required by Spring Security.
 *
 * Separated from SecurityConfig to avoid circular dependency issues:
 *   SecurityConfig → JwtAuthenticationFilter → UserDetailsService → UserRepository
 *
 * Beans defined here:
 *   - UserDetailsService:    loads a User entity by email for authentication
 *   - PasswordEncoder:       BCrypt hashing for passwords
 *   - AuthenticationProvider: wires UserDetailsService + PasswordEncoder together
 *   - AuthenticationManager: the entry point for programmatic authentication (used in AuthService)
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;

    /**
     * Loads a User from the database by email address.
     * Spring Security calls this during login to retrieve the stored credentials.
     *
     * We map our User entity to Spring Security's UserDetails interface so
     * the framework can compare the provided password against the stored hash.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPasswordHash())
                        // Roles are prefixed with "ROLE_" by Spring Security conventions
                        .roles(user.getRole())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No account found for: " + username));
    }

    /**
     * BCrypt password encoder with default strength (10 rounds).
     * Increase the strength for production if compute budget allows.
     *
     * Never store or log plain text passwords — always hash with this encoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires our UserDetailsService and PasswordEncoder into Spring Security's
     * standard DAO authentication flow. This handles the actual credential check
     * during login.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 7.x: UserDetailsService is required in the constructor.
        // The no-arg constructor and setUserDetailsService() were removed.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes Spring Security's AuthenticationManager as a bean so it can be
     * injected into AuthService for programmatic authentication (login flow).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}