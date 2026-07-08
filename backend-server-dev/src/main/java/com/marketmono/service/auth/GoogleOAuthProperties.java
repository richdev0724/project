package com.marketmono.service.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "app.oauth.google")
@Getter
@Setter
public class GoogleOAuthProperties {
    private String clientId = "";
    private String allowedDomains = "";
    private boolean requireEmailVerified = true;
    private boolean autoLinkUsers = true;
    private boolean autoCreateUsers = true;
    private String jwksUri = "https://www.googleapis.com/oauth2/v3/certs";
    private Duration jwksConnectTimeout = Duration.ofSeconds(2);
    private Duration jwksReadTimeout = Duration.ofSeconds(3);
    private Duration keyMissRefreshCooldown = Duration.ofMinutes(5);

    public Set<String> allowedDomainSet() {
        if (allowedDomains == null || allowedDomains.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(allowedDomains.split(","))
            .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
            .filter(domain -> !domain.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
