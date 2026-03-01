package com.app.craftyhire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures the HTTP client used to communicate with the Anthropic Claude API.
 *
 * A shared RestClient bean is used by both ClaudeService (document generation)
 * and JobAnalysisService (skill extraction) to avoid duplicating API setup.
 *
 * Scalability note: If additional external APIs are added in the future
 * (e.g., LinkedIn, job board APIs), add new RestClient beans here with
 * @Qualifier annotations to distinguish them.
 */
@Configuration
public class ApiClientConfig {

    @Value("${anthropic.api.base-url}")
    private String baseUrl;

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.version}")
    private String apiVersion;

    /**
     * Pre-configured RestClient for the Anthropic Claude API.
     *
     * Sets the base URL and required headers on every request:
     *   - x-api-key:         your Anthropic API key (from env var ANTHROPIC_API_KEY)
     *   - anthropic-version: API version pinned for stability
     *   - Content-Type:      all requests send JSON
     */
    @Bean
    public RestClient claudeRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", apiVersion)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}