package com.marketmono.config.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP channel interceptor that validates JWT tokens during CONNECT.
 *
 * <p>The SockJS HTTP handshake (/ws/**) remains open (no Spring Security auth)
 * because SockJS needs multiple HTTP requests (info, iframe, xhr-polling) that
 * cannot carry Authorization headers. Instead, authentication happens at the
 * STOMP protocol level:
 *
 * <ol>
 *   <li>Client opens SockJS transport (unauthenticated HTTP)</li>
 *   <li>Client sends STOMP CONNECT with {@code Authorization: Bearer <jwt>} header</li>
 *   <li>This interceptor validates the JWT and sets the authenticated principal</li>
 *   <li>Unauthenticated CONNECT frames are rejected with an error</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    var userId = jwtTokenProvider.getUserIdFromToken(token);
                    var roles = jwtTokenProvider.getRolesFromToken(token);
                    var authorities = roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                            .toList();
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId.toString(), null, authorities);
                    accessor.setUser(auth);
                    log.debug("WebSocket CONNECT authenticated for user {}", userId);
                    return message;
                }
            }
            log.warn("WebSocket CONNECT rejected: missing or invalid Authorization header");
            throw new org.springframework.messaging.MessageDeliveryException(
                    "Authentication required for WebSocket connection");
        }

        return message;
    }
}
