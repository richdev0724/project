package com.marketmono.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(
        mock(InternalAdminApiAuthenticationFilter.class),
        mock(JwtAuthenticationFilter.class),
        mock(PasswordChangeRequiredFilter.class),
        mock(CustomUserDetailsService.class),
        mock(SubscriptionAccessFilter.class)
    );

    @Test
    void shouldAllowBareProductionOrigins() {
        var source = securityConfig.corsConfigurationSource();
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        var configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://marketmono.com")).isEqualTo("https://marketmono.com");
        assertThat(configuration.checkOrigin("https://www.marketmono.com")).isEqualTo("https://www.marketmono.com");
    }

    @Test
    void shouldStillAllowSubdomainOrigins() {
        var source = securityConfig.corsConfigurationSource();
        var request = new MockHttpServletRequest("GET", "/ws/info");
        var configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://app.marketmono.com")).isEqualTo("https://app.marketmono.com");
    }
}
