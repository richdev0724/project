package com.marketmono.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for subscription management.
 * Binds to subscription.* properties in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "subscription")
@Getter
@Setter
public class SubscriptionProperties {
    
    /**
     * Number of days to allow access for PAST_DUE subscriptions.
     * - 0 = immediate revocation of access when payment fails
     * - 1 = 1 day grace period (default)
     * - 3+ = longer grace period for enterprise customers
     */
    private int pastDueGracePeriodDays = 1;
}
