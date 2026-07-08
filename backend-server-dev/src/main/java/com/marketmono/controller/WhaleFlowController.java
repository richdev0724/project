package com.marketmono.controller;

import com.marketmono.dto.WhaleTradePageResponse;
import com.marketmono.service.WhaleFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for whale trades endpoints
 * Handles large options trades (premium >= $10k)
 */
@RestController
@RequestMapping("/api/v1/whale-trades")
@CrossOrigin(origins = "*")
public class WhaleFlowController {
    
    private static final Logger log = LoggerFactory.getLogger(WhaleFlowController.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes
    private static final long HEARTBEAT_INTERVAL = 15 * 1000L; // 15 seconds
    
    private final WhaleFlowService whaleFlowService;
    
    // SSE emitter management
    private final Map<SseEmitter, String> activeEmitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public WhaleFlowController(WhaleFlowService whaleFlowService) {
        this.whaleFlowService = whaleFlowService;
        // Start periodic whale trade broadcast and heartbeat
        startWhaleTradeBroadcaster();
        startHeartbeat();
    }
    
    /**
     * Get whale trades with pagination and filtering
     * GET /api/v1/whale-trades?page=1&pageSize=50&minPremium=10000&sortBy=timestamp
     */
    @GetMapping
    public ResponseEntity<WhaleTradePageResponse> getWhaleTrades(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "50") int pageSize,
        @RequestParam(required = false) BigDecimal minPremium,
        @RequestParam(required = false) BigDecimal maxPremium,
        @RequestParam(required = false) Integer minSize,
        @RequestParam(required = false) Integer maxSize,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String session,
        @RequestParam(required = false) String ticker,
        @RequestParam(defaultValue = "timestamp") String sortBy
    ) {
        log.debug("GET /api/v1/whale-trades - page={}, pageSize={}, minPremium={}, ticker={}, sortBy={}", 
            page, pageSize, minPremium, ticker, sortBy);
        
        // Validate and constrain page size
        int effectivePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        int effectivePage = Math.max(1, page);
        
        WhaleTradePageResponse response = whaleFlowService.getWhaleTradesData(
            effectivePage,
            effectivePageSize,
            minPremium,
            maxPremium,
            minSize,
            maxSize,
            session,
            ticker,
            sortBy
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get top whale trades by premium
     * GET /api/v1/whale-trades/top?limit=10
     */
    @GetMapping("/top")
    public ResponseEntity<WhaleTradePageResponse> getTopWhaleTrades(
        @RequestParam(defaultValue = "10") int limit
    ) {
        log.debug("GET /api/v1/whale-trades/top - limit={}", limit);
        
        // Constrain limit
        int effectiveLimit = Math.min(Math.max(1, limit), 50);
        
        WhaleTradePageResponse response = whaleFlowService.getTopWhaleTrades(effectiveLimit);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stream whale trades via Server-Sent Events (SSE)
     * GET /api/v1/whale-trades/stream
     * Sends new whale trades as they are detected
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWhaleTrades() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        String clientId = "client-" + System.currentTimeMillis();
        activeEmitters.put(emitter, clientId);
        log.info("SSE client connected: {} (total clients: {})", clientId, activeEmitters.size());
        
        // Remove emitter on completion or timeout
        emitter.onCompletion(() -> {
            activeEmitters.remove(emitter);
            log.info("SSE client completed: {} (remaining clients: {})", clientId, activeEmitters.size());
        });
        
        emitter.onTimeout(() -> {
            activeEmitters.remove(emitter);
            log.info("SSE client timeout: {} (remaining clients: {})", clientId, activeEmitters.size());
        });
        
        emitter.onError(ex -> {
            activeEmitters.remove(emitter);
            log.error("SSE client error: {} - {}", clientId, ex.getMessage());
        });
        
        // Send initial connection confirmation
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            log.error("Failed to send connection confirmation to {}: {}", clientId, e.getMessage());
            activeEmitters.remove(emitter);
        }
        
        return emitter;
    }
    
    /**
     * Periodically broadcast new whale trades to all connected SSE clients
     * Checks for new whale trades every 5 seconds
     */
    private void startWhaleTradeBroadcaster() {
        scheduler.scheduleAtFixedRate(() -> {
            if (activeEmitters.isEmpty()) {
                return;
            }
            
            try {
                // Fetch latest whale trades (last 10)
                WhaleTradePageResponse latestTrades = whaleFlowService.getTopWhaleTrades(10);
                
                if (latestTrades.data() != null && !latestTrades.data().isEmpty()) {
                    broadcastToClients("whale-trade", latestTrades.data());
                }
            } catch (Exception e) {
                log.error("Error broadcasting whale trades: {}", e.getMessage(), e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Send periodic heartbeat to keep connections alive
     */
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (activeEmitters.isEmpty()) {
                return;
            }
            
            broadcastToClients("heartbeat", Map.of("timestamp", System.currentTimeMillis()));
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Broadcast data to all connected SSE clients
     */
    private void broadcastToClients(String eventName, Object data) {
        activeEmitters.keySet().forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (IOException e) {
                log.warn("Failed to send event '{}' to client: {}", eventName, e.getMessage());
                activeEmitters.remove(emitter);
            }
        });
    }
}
