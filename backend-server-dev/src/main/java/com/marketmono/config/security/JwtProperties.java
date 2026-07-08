package com.marketmono.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties.
 * Configure in application.yml under 'jwt' prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    
    /**
     * Secret key for signing JWT tokens (must be at least 256 bits / 32 chars for HS256).
     */
    private String secret = "";
    
    /**
     * Access token expiration time in milliseconds (default: 15 minutes).
     */
    private long accessTokenExpiration = 15L * 60 * 1000; // 15 minutes
    
    /**
     * Refresh token expiration time in milliseconds (default: 7 days).
     */
    private long refreshTokenExpiration = 7L * 24 * 60 * 60 * 1000; // 7 days
    
    /**
     * JWT issuer claim.
     */
    private String issuer = "marketmono";
}
