package com.marketmono.service;

import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.WhaleFlowAlertDto;
import com.marketmono.entity.FlowAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async broadcaster component for flow alerts via WebSocket.
 * Runs on virtual threads to handle non-blocking broadcasting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowAlertBroadcaster {
    
    private final WebSocketRelayPublisher webSocketRelayPublisher;
    private final WebSocketFlowAlertMapperService wsFlowAlertMapper;
    
    /**
     * Broadcast complete flow alert data to frontend clients via STOMP WebSocket.
     * Converts FlowAlert entity to full WhaleFlowAlertDto with all fields for table rendering.
     * Uses virtual threads for non-blocking async execution.
     * 
     * @param flowAlert Flow alert entity with all database fields populated
     */
    @Async
    public void broadcastFlowAlert(FlowAlert flowAlert) {
        try {
            var wsDto = wsFlowAlertMapper.toWebSocketDto(flowAlert);
            webSocketRelayPublisher.publish("/topic/flow-alerts", wsDto);
            log.debug("📡 Broadcasted complete alert {} to /topic/flow-alerts", flowAlert.getAlertId());
        } catch (Exception e) {
            log.error("Error broadcasting alert {} to clients: {}", flowAlert.getAlertId(), e.getMessage());
        }
    }
    
    /**
     * Broadcast flow alert data immediately from raw WebSocket DTO.
     * This bypasses database operations for real-time updates.
     * Uses virtual threads for non-blocking async execution.
     * 
     * @param rawDto Raw UnusualWhalesFlowAlertDto from WebSocket
     */
    @Async
    public void broadcastFlowAlertImmediately(UnusualWhalesFlowAlertDto rawDto) {
        try {
            var wsDto = wsFlowAlertMapper.toWebSocketDtoFromRaw(rawDto);
            webSocketRelayPublisher.publish("/topic/flow-alerts", wsDto);
            log.debug("📡 Broadcasted immediate alert {} to /topic/flow-alerts", rawDto.id());
        } catch (Exception e) {
            log.error("Error broadcasting immediate alert {} to clients: {}", rawDto.id(), e.getMessage());
        }
    }
}
