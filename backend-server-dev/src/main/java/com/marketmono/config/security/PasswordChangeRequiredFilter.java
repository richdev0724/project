package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Restricts temporary-password users to account-completion endpoints until they
 * set a permanent password.
 */
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PATHS = List.of(
        "/api/v1/auth/me",
        "/api/v1/auth/change-password",
        "/api/v1/auth/logout",
        "/api/v1/auth/logout-all",
        "/api/v1/auth/refresh"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = principal.getUser();
        if (!Boolean.TRUE.equals(user.getRequiresPasswordChange()) || isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"error":"Password change required","message":"Please change your temporary password before using Market Mono.","code":"PASSWORD_CHANGE_REQUIRED"}""");
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ALLOWED_PATHS.stream().anyMatch(path::equals);
    }
}
