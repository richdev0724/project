package com.marketmono.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpUtilsTest {

    @Test
    void withRealIpFallbackUsesForwardedForFirstAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.5");

        assertThat(HttpUtils.getClientIpWithRealIpFallback(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void withRealIpFallbackUsesRealIpBeforeRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.20");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        assertThat(HttpUtils.getClientIpWithRealIpFallback(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void withoutRealIpFallbackUsesRemoteAddressWhenForwardedForIsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        assertThat(HttpUtils.getClientIpWithoutRealIpFallback(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void withoutRealIpFallbackIgnoresRealIpHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.20");
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");

        assertThat(HttpUtils.getClientIpWithoutRealIpFallback(request)).isEqualTo("10.0.0.9");
    }
}
