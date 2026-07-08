package com.marketmono.config;

import com.marketmono.config.security.WebSocketNodeIdentityInterceptor;
import com.marketmono.config.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * WebSocket configuration for real-time flow alerts broadcasting via STOMP protocol.
 * 
 * <p>Architecture:
 * <pre>
 * Market data WebSocket -> UnusualWhalesWebSocketClient
 *                      ↓
 *               FlowAlertService.handleWebSocketFlowAlert()
 *                      ↓ (saves to DB + broadcasts)
 *               SimpMessagingTemplate.convertAndSend()
 *                      ↓
 *               STOMP Broker (/topic/flow-alerts)
 *                      ↓
 *               Frontend (STOMP.js client)
 * </pre>
 * 
 * <p>STOMP Endpoints:
 * <ul>
 *   <li><b>/ws</b> - WebSocket handshake endpoint (with SockJS fallback)</li>
 *   <li><b>/topic/flow-alerts</b> - Broadcast channel for all flow alerts</li>
 *   <li><b>/app/*</b> - Client-to-server message prefix (future use)</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>Simple in-memory broker for development (can upgrade to RabbitMQ/ActiveMQ)</li>
 *   <li>SockJS fallback for browsers without WebSocket support</li>
 *   <li>CORS configured for local development (Vite on :5173, Node.js on :3001)</li>
 *   <li>Virtual threads used for async broadcasting (@Async in FlowAlertService)</li>
 * </ul>
 * 
 * @see com.marketmono.service.FlowAlertService#broadcastToClients(com.marketmono.dto.UnusualWhalesFlowAlertDto)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketNodeIdentityInterceptor webSocketNodeIdentityInterceptor;
    
    @Value("${websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://[::1]:*}")
    private String allowedOriginPatterns;
    
    /**
     * Configure message broker for STOMP messaging.
     * 
     * <p>Broker Destinations:
     * <ul>
     *   <li><b>/topic/*</b> - Pub/sub destinations (many subscribers)</li>
     *   <li><b>/queue/*</b> - Point-to-point destinations (single consumer, future use)</li>
     * </ul>
     * 
     * <p>Application Destination Prefix:
     * <ul>
     *   <li><b>/app/*</b> - Messages from clients to @MessageMapping controllers</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /topic and /queue destinations
        config.enableSimpleBroker("/topic", "/queue");
        
        // Set prefix for client messages to application
        config.setApplicationDestinationPrefixes("/app");
        
        log.info("STOMP message broker configured: /topic, /queue (in-memory)");
    }
    
    /**
     * Register STOMP endpoints for WebSocket connections.
     * 
     * <p>Endpoint: <b>/ws</b>
     * <ul>
     *   <li>URL: ws://localhost:8080/ws (WebSocket native)</li>
     *   <li>URL: http://localhost:8080/ws (SockJS fallback)</li>
     * </ul>
     * 
     * <p>CORS Configuration:
     * <ul>
     *   <li>Configured via <code>websocket.allowed-origin-patterns</code> property</li>
     *   <li>Default: localhost and 127.0.0.1 on all ports</li>
     *   <li>Production: Set via environment variable WEBSOCKET_ALLOWED_ORIGIN_PATTERNS</li>
     *   <li>Supports wildcards: Use <code>http://*:*</code> to allow all HTTP origins (dev only!)</li>
     * </ul>
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Parse allowed origin patterns from comma-separated list
        List<String> originPatterns = Arrays.stream(allowedOriginPatterns.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(originPatterns.toArray(new String[0]))
            .withSockJS();  // Enable SockJS fallback (HTTP long-polling, etc.)
        
        log.info("STOMP endpoint registered: /ws (allowed origin patterns: {})", String.join(", ", originPatterns));
    }
    
    /**
     * Register the JWT authentication interceptor for inbound STOMP messages.
     * Validates the Authorization header during STOMP CONNECT.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketNodeIdentityInterceptor);
    }
}
