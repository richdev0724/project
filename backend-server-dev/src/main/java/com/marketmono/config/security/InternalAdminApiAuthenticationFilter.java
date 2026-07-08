package com.marketmono.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Authenticates selected internal backend-admin requests using a shared secret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAdminApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Internal-Admin-Secret";
    public static final String ACTOR_ID_HEADER = "X-Internal-Admin-Actor-Id";

    private static final String STRIPE_ADMIN_PATH_PREFIX = "/api/v1/admin/stripe/";
    private static final String COLLECTION_CONFIG_ADMIN_PATH_PREFIX = "/api/v1/admin/collection-config/";
    private static final String CHANGELOG_ADMIN_PATH_PREFIX = "/api/v1/admin/changelog";
    private static final String WAITLIST_ADMIN_PATH_PREFIX = "/api/v1/admin/waitlist";
    private static final String BETA_WAITLIST_INVITE_ADMIN_PATH_PREFIX = "/api/v1/admin/waitlist/beta-invites";
    private static final String EMAIL_ADMIN_PATH_PREFIX = "/api/v1/admin/email";
    private static final String CUSTOMER_INVITE_ADMIN_PATH_PREFIX = "/api/v1/admin/customer-invites";
    private static final String ECONOMIC_EVENT_ADMIN_PATH_PREFIX = "/api/v1/admin/economic-events";
    private static final String APP_USER_ADMIN_PATH_PREFIX = "/api/v1/admin/app-users";
    private static final String WHOP_ADMIN_PATH_PREFIX = "/api/v1/admin/whop/";

    private final InternalAdminApiProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return !servletPath.startsWith(STRIPE_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(COLLECTION_CONFIG_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(CHANGELOG_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(WAITLIST_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(EMAIL_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(CUSTOMER_INVITE_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(APP_USER_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(WHOP_ADMIN_PATH_PREFIX)
            && !servletPath.startsWith(ECONOMIC_EVENT_ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled() || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedSecret = request.getHeader(SECRET_HEADER);
        if (!StringUtils.hasText(providedSecret) || !providedSecret.equals(properties.sharedSecret())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID adminId = parseActorId(
            request.getHeader(ACTOR_ID_HEADER),
            requiresActorId(request.getServletPath()),
            response
        );
        if (response.isCommitted()) {
            return;
        }

        InternalAdminPrincipal principal = new InternalAdminPrincipal(adminId);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated internal backend-admin request for {}", request.getServletPath());

        filterChain.doFilter(request, response);
    }

    private boolean requiresActorId(String servletPath) {
        return servletPath.startsWith(CUSTOMER_INVITE_ADMIN_PATH_PREFIX)
            || servletPath.startsWith(APP_USER_ADMIN_PATH_PREFIX)
            || servletPath.startsWith(WHOP_ADMIN_PATH_PREFIX)
            || servletPath.startsWith(BETA_WAITLIST_INVITE_ADMIN_PATH_PREFIX)
            || servletPath.startsWith(ECONOMIC_EVENT_ADMIN_PATH_PREFIX);
    }

    private UUID parseActorId(
            String actorIdHeader,
            boolean actorRequired,
            HttpServletResponse response) throws IOException {
        if (!StringUtils.hasText(actorIdHeader)) {
            if (actorRequired) {
                log.warn("Rejected internal backend-admin request without actor id header");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing internal admin actor id header");
            }
            return null;
        }

        try {
            return UUID.fromString(actorIdHeader);
        } catch (IllegalArgumentException ex) {
            log.warn("Rejected internal backend-admin request with invalid actor id header");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid internal admin actor id header");
            return null;
        }
    }
}
