package com.marketmono.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for Whop webhook verification.
 */
@Configuration
@ConfigurationProperties(prefix = "whop.webhook")
public class WhopWebhookProperties {

    private String secret;
    private Duration signatureTolerance = Duration.ofMinutes(5);

    public WhopWebhookProperties() {
    }

    public WhopWebhookProperties(String secret, Duration signatureTolerance) {
        this.secret = secret;
        this.signatureTolerance = signatureTolerance == null ? Duration.ofMinutes(5) : signatureTolerance;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getSignatureTolerance() {
        return signatureTolerance == null ? Duration.ofMinutes(5) : signatureTolerance;
    }

    public void setSignatureTolerance(Duration signatureTolerance) {
        this.signatureTolerance = signatureTolerance == null ? Duration.ofMinutes(5) : signatureTolerance;
    }
}
