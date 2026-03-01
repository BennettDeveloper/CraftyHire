package com.app.craftyhire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 *
 * The React frontend (running on a different port/domain) needs to be
 * explicitly allowed to call this API. Without CORS configuration, browsers
 * will block all cross-origin requests.
 *
 * Allowed origins are configured in application.properties under
 * `cors.allowed-origins`. Multiple origins can be comma-separated.
 *
 * Scalability note: In production, replace localhost:3000 with your
 * deployed frontend URL(s). Consider restricting methods/headers further
 * if the API is exposed publicly.
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed frontend origins.
     * Default: http://localhost:3000 (React dev server)
     * Example production value: https://craftyhire.com,https://www.craftyhire.com
     */
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Defines CORS rules applied to all API endpoints ("/**").
     * This bean is injected into SecurityConfig so the same rules
     * apply both at the Spring Security layer and MVC layer.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse comma-separated origins from config
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        // Allow standard HTTP methods used by this REST API
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow all request headers (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Allow cookies and Authorization headers in cross-origin requests
        config.setAllowCredentials(true);

        // Apply this configuration to all routes
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}