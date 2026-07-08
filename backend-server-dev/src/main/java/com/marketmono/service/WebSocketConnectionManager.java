package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages WebSocket connection lifecycle and health monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketConnectionManager {
    
    private final UnusualWhalesWebSocketClient webSocketClient;
    private final UnusualWhalesConfig config;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    private Disposable connectionDisposable;
    
    /**
     * Start WebSocket connection on application startup
     */
    @PostConstruct
    public void start() {
        log.info("WS config - enabled: {}, url: {}, reconnectInterval: {}, maxAttempts: {}",
            config.getWebsocket().isEnabled(),
            config.getWebsocket().getUrl(),
            config.getWebsocket().getReconnectIntervalSeconds(),
            config.getWebsocket().getMaxReconnectAttempts());

        if (!config.getWebsocket().isEnabled()) {
            log.info("WS connection disabled via configuration");
            return;
        }
        
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting WS connection manager");
            connectWebSocket();
        }
    }
    
    /**
     * Stop WebSocket connection on application shutdown
     */
    @PreDestroy
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping WS connection manager");
            if (connectionDisposable != null && !connectionDisposable.isDisposed()) {
                connectionDisposable.dispose();
            }
        }
    }
    
    /**
     * Establish WebSocket connection
     */
    private void connectWebSocket() {
        log.info("Establishing market data websocket connection");
        
        connectionDisposable = webSocketClient.connect()
            .delaySubscription(Duration.ofSeconds(2)) // Small delay on startup
            .doOnSubscribe(sub -> log.info("Initiating WS connection"))
            .doOnSuccess(v -> log.info("WS connection established successfully"))
            .doOnError(error -> {
                log.error("WS connection failed", error);
                isRunning.set(false);
            })
            .subscribe();
    }
    
    /**
     * Check if WebSocket is currently connected
     */
    public boolean isConnected() {
        return webSocketClient.isConnected();
    }
    
    /**
     * Get current connection state
     */
    public ConnectionState getConnectionState() {
        if (!config.getWebsocket().isEnabled()) {
            return ConnectionState.DISABLED;
        }
        if (webSocketClient.isConnected()) {
            return ConnectionState.CONNECTED;
        }
        if (isRunning.get()) {
            return ConnectionState.CONNECTING;
        }
        return ConnectionState.DISCONNECTED;
    }
    
    public enum ConnectionState {
        DISABLED,
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }
}
