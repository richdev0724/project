package com.marketmono.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP request helper methods shared by controllers and services.
 */
public final class HttpUtils {

    private HttpUtils() {
    }

    public static String getClientIpWithRealIpFallback(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return firstForwardedIp(xForwardedFor);
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    public static String getClientIpWithoutRealIpFallback(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return firstForwardedIp(xForwardedFor);
        }
        return request.getRemoteAddr();
    }

    private static String firstForwardedIp(String xForwardedFor) {
        return xForwardedFor.split(",")[0].trim();
    }
}
