package com.marketmono.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "whop.checkout")
@Getter
@Setter
public class WhopCheckoutProperties {

    private String apiKey;
    private String companyId;
    private String apiBaseUrl = "https://api.whop.com/api/v1";
    private String redirectUrl;
    private String sourceUrl;
    private boolean allowPromoCodes = true;
    private Duration requestTimeout = Duration.ofSeconds(15);

    public boolean isConfigured() {
        return hasText(apiKey) && hasText(companyId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
