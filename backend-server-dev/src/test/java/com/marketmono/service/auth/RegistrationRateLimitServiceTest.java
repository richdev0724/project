package com.marketmono.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationRateLimitServiceTest {

    @Test
    void shouldUseRealIpHeaderWhenForwardedForIsMissing() {
        RegistrationRateLimitService service = new RegistrationRateLimitService();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.20");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        service.recordAttempt(request);
        service.recordAttempt(request);
        service.recordAttempt(request);

        assertThat(service.shouldRequireCaptcha(request)).isTrue();
    }
}
