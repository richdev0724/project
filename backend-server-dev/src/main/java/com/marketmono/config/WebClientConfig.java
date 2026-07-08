package com.marketmono.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Web client configuration for external API calls
 * Provides shared WebClient beans for different API clients
 */
@Configuration
public class WebClientConfig {
    
    /**
     * Create default WebClient bean for HTTP clients that don't need custom configuration
     * Used by Congress.gov API client and other simple HTTP clients
     * 
     * @return Default WebClient instance
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
