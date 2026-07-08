package com.marketmono.config.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigUwRouteManifestTest {

    private static final Path SECURITY_CONFIG = Path.of(
        "src/main/java/com/marketmono/config/security/SecurityConfig.java"
    );

    private static final List<String> UW_PROXY_ROUTE_PATTERNS = List.of(
        "/api/v1/congress-recent-trades/**",
        "/api/v1/greek-exposure-by-strike/**",
        "/api/v1/greek-exposure-strike-expiry/**",
        "/api/v1/ticker-options-volume/**",
        "/api/v1/darkpool/**",
        "/api/v1/spot-exposure-by-strike-expiry/**",
        "/api/v1/analyst-ratings/**",
        "/api/v1/insider-trades/**",
        "/api/v1/option-chains/**",
        "/api/v1/price-change-per-month/**",
        "/api/v1/earnings-reports/**",
        "/api/v1/stock-information/**",
        "/api/v1/seasonality/**",
        "/api/v1/stock/**",
        "/api/v1/options-price-levels/**",
        "/api/v1/option-contract-historic/**",
        "/api/v1/option-contract-intraday/**",
        "/api/v1/option-contracts-all/**",
        "/api/v1/iv-rank/**",
        "/api/v1/realized-volatility/**",
        "/api/v1/GEX-Strike-Expiry-Range/**",
        "/api/v1/institution/**",
        "/api/v1/etfs/**",
        "/api/v1/option-trades/screener/option-contracts",
        "/api/stock/**",
        "/api/darkpool/**"
    );

    private static final List<String> APPROVED_PUBLIC_NON_UW_ROUTE_PATTERNS = List.of(
        "/api/v1/ticker-bio-info/**",
        "/api/v1/congress-member-details/**",
        "/api/v1/changelog/**",
        "/api/v1/econ_events",
        "/api/v1/econ_events/**"
    );

    @Test
    void shouldNotPermitAllKnownUwProxyRoutes() throws IOException {
        String securityConfig = Files.readString(SECURITY_CONFIG);

        for (String routePattern : UW_PROXY_ROUTE_PATTERNS) {
            assertThat(hasPermitAllMatcher(securityConfig, routePattern))
                .as("UW proxy route must not be permitAll: %s", routePattern)
                .isFalse();
        }
    }

    @Test
    void shouldKeepApprovedNonUwRoutesPublic() throws IOException {
        String securityConfig = Files.readString(SECURITY_CONFIG);

        for (String routePattern : APPROVED_PUBLIC_NON_UW_ROUTE_PATTERNS) {
            assertThat(hasPermitAllMatcher(securityConfig, routePattern))
                .as("Approved non-UW route should remain permitAll: %s", routePattern)
                .isTrue();
        }
    }

    private static boolean hasPermitAllMatcher(String securityConfig, String routePattern) {
        Pattern permitAllMatcher = Pattern.compile("\\.requestMatchers\\([\\s\\S]*?\\)\\.permitAll\\(\\)");
        return permitAllMatcher.matcher(securityConfig)
            .results()
            .map(match -> match.group())
            .anyMatch(matcher -> matcher.contains("\"" + routePattern + "\""));
    }
}
