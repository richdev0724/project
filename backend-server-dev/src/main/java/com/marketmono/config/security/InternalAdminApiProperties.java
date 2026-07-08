package com.marketmono.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for internal backend-admin to backend-server admin API calls.
 */
@ConfigurationProperties(prefix = "internal.admin-api")
public record InternalAdminApiProperties(
    String sharedSecret
) {
    public boolean enabled() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }
}