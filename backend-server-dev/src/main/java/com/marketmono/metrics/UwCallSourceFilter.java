package com.marketmono.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class UwCallSourceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try (UwCallSourceContext.Scope ignored = UwCallSourceContext.open(sourceFor(request))) {
            filterChain.doFilter(request, response);
        }
    }

    private UwCallSource sourceFor(HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : null;
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return normalizedPath.startsWith("/admin") ? UwCallSource.ADMIN_REQUEST : UwCallSource.USER_REQUEST;
    }
}
