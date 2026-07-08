package com.marketmono.config.security;

import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

/**
 * JWT authentication filter that validates tokens and sets authentication context.
 * Always processes JWT if present — public endpoints don't require a token,
 * but if one is provided the auth context is set for downstream use.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Always process JWT if present — even for public endpoints.
            // Public endpoints don't REQUIRE a token, but if one is provided
            // we set the auth context so downstream filters (e.g. SubscriptionAccessFilter)
            // and controllers (e.g. @AuthenticationPrincipal) can identify the user.
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                UUID userId = jwtTokenProvider.getUserIdFromToken(jwt);
                
                // Load full user from database
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (!isActiveAccount(user)) {
                        log.warn("Rejecting JWT authentication for inactive user: {}", user.getEmail());
                        filterChain.doFilter(request, response);
                        return;
                    }
                    UserPrincipal userPrincipal = new UserPrincipal(user);
                    
                    // Create authentication token with UserPrincipal
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                            userPrincipal,
                            null,
                            userPrincipal.getAuthorities()
                        );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated user: {} with roles: {}", user.getEmail(), userPrincipal.getAuthorities());
                } else {
                    log.warn("User not found for ID from JWT: {}", userId);
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extract JWT token from Authorization header.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isActiveAccount(User user) {
        return Boolean.TRUE.equals(user.getEnabled())
            && !user.isDeleted()
            && !user.isBanned()
            && !user.isAccountLocked();
    }
}
