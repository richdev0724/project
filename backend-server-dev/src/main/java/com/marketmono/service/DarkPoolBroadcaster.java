package com.marketmono.service;

import com.marketmono.dto.DarkPoolTopActivityDto;
import com.marketmono.dto.LitPoolTopActivityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async broadcaster component for dark pool and lit pool updates via WebSocket.
 * Runs on virtual threads to handle non-blocking broadcasting.
 * 
 * <p>Architecture (same pattern as FlowAlertBroadcaster):
 * <pre>
 * DpLpService.saveTrades() → DarkPoolBroadcaster
 *                                  ↓
 *                          SimpMessagingTemplate.convertAndSend()
 *                                  ↓
 *                          STOMP Broker (/topic/dark-pool-updates, /topic/lit-pool-updates)
 *                                  ↓
 *                          Frontend (STOMP.js client)
 * </pre>
 * 
 * @see com.marketmono.service.DpLpService#saveTrades(java.util.List)
 * @see com.marketmono.service.FlowAlertBroadcaster
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DarkPoolBroadcaster {
    
    private final WebSocketRelayPublisher webSocketRelayPublisher;
    
    /**
     * Broadcast dark pool top activity update to frontend clients via STOMP WebSocket.
     * Uses virtual threads for non-blocking async execution.
     * 
     * @param dto Dark pool top activity DTO with symbol and dark volume
     */
    @Async
    public void broadcastDarkPoolUpdate(DarkPoolTopActivityDto dto) {
        try {
            webSocketRelayPublisher.publish("/topic/dark-pool-updates", dto);
            log.debug("Broadcasted dark pool update for {} to frontend clients", dto.symbol());
        } catch (Exception e) {
            log.error("Error broadcasting dark pool update for {}: {}", dto.symbol(), e.getMessage());
        }
    }
    
    /**
     * Broadcast lit pool top activity update to frontend clients via STOMP WebSocket.
     * Uses virtual threads for non-blocking async execution.
     * 
     * @param dto Lit pool top activity DTO with symbol and lit volume
     */
    @Async
    public void broadcastLitPoolUpdate(LitPoolTopActivityDto dto) {
        try {
            webSocketRelayPublisher.publish("/topic/lit-pool-updates", dto);
            log.debug("Broadcasted lit pool update for {} to frontend clients", dto.symbol());
        } catch (Exception e) {
            log.error("Error broadcasting lit pool update for {}: {}", dto.symbol(), e.getMessage());
        }
    }
}
