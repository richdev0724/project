package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.SubscriptionService;
import com.marketmono.config.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter to enforce subscription-based access control.
 * Unsubscribed users can only access profile, products, and subscription endpoints.
 * All other endpoints require website access. Billing grace states can remain
 * visible in subscription summaries without unlocking protected data endpoints.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAccessFilter extends OncePerRequestFilter {
    
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    
    /**
     * Endpoints accessible without an active subscription.
     */
    private static final List<String> ALLOWED_WITHOUT_SUBSCRIPTION = List.of(
        "/api/v1/auth/",
        "/api/v1/admin/",
        "/api/v1/changelog",
        "/api/v1/profile",  // Removed trailing slash to match actual requests
        "/api/v1/products/storefront",
        "/api/v1/products/summaries",
        "/api/v1/subscriptions/",
        "/api/v1/support/",
        "/api/v1/stripe/status",
        "/api/v1/stripe/public-key",
        "/api/v1/stripe/webhook",
        "/api/v1/whop/webhooks",
        "/api/v1/whop/checkout/",
        "/api/v1/stripe/checkout/",  // Checkout Session endpoints (for new subscriptions)
        "/api/v1/stripe/portal/",  // Customer Portal endpoints (for existing subscriptions)
        "/api/v1/billing/portal/",  // Provider-neutral billing portal endpoint
        "/actuator/"
    );
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        log.debug("SubscriptionAccessFilter checking path: {}", requestPath);
        
        // Skip filter for endpoints that don't require subscription
        if (isAllowedWithoutSubscription(requestPath)) {
            log.debug("Path {} is whitelisted, allowing access", requestPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("Path {} is NOT whitelisted, checking authentication", requestPath);
        
        // Get authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() 
                || "anonymousUser".equals(authentication.getName())) {
            // Not authenticated - let Spring Security handle it
            filterChain.doFilter(request, response);
            return;
        }

        if (authentication.getPrincipal() instanceof InternalAdminPrincipal) {
            log.debug("Skipping subscription access check for internal admin principal on {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract user ID directly from UserPrincipal (set by JwtAuthenticationFilter)
        UUID userId = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            userId = userPrincipal.getUser().getId();
        } else {
            // Fallback: try to find user by email (authentication.getName())
            String userEmail = authentication.getName();
            userId = userRepository.findByEmail(userEmail)
                .map(User::getId)
                .orElse(null);
        }
        
        if (userId == null) {
            log.warn("Authenticated user not found in database: {}", authentication.getName());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }
        
        // Check for website access.
        boolean hasActiveSubscription = subscriptionService.hasWebsiteAccess(userId);
        
        if (!hasActiveSubscription) {
            log.info("Access denied for unsubscribed user: {} to {}", authentication.getName(), requestPath);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Active subscription required\", " +
                "\"message\": \"Please subscribe to access this resource\", " +
                "\"code\": \"SUBSCRIPTION_REQUIRED\"}");
            return;
        }
        
        // User has active subscription - allow access
        filterChain.doFilter(request, response);
    }
    
    /**
     * Check if endpoint is accessible without subscription.
     */
    private boolean isAllowedWithoutSubscription(String path) {
        return ALLOWED_WITHOUT_SUBSCRIPTION.stream()
            .anyMatch(allowed -> {
                if (allowed.endsWith("/")) {
                    // For trailing slash entries, match if path starts with the allowed prefix
                    return path.startsWith(allowed);
                } else {
                    // For non-trailing slash entries, exact match or path starts with allowed + "/"
                    return path.equals(allowed) || path.startsWith(allowed + "/");
                }
            });
    }
}
