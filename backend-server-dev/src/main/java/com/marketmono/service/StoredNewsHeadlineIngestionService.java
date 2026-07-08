package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.NewsHeadlineDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Subscribes the persisted market-data news feed to the upstream WebSocket.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoredNewsHeadlineIngestionService {

    private final UnusualWhalesWebSocketClient webSocketClient;
    private final UnusualWhalesConfig config;
    private final StoredNewsHeadlineWriter writer;

    @PostConstruct
    public void initialize() {
        if (config.getWebsocket().isEnabled()) {
            log.info("[UW-NEWS] Registering news WebSocket handler");
            webSocketClient.onNewsHeadline(this::handleNewsHeadline);
        }
    }

    private void handleNewsHeadline(NewsHeadlineDto dto) {
        try {
            writer.saveFromWebSocket(dto);
        } catch (Exception e) {
            log.error("[UW-NEWS] Error handling news headline: {}", e.getMessage(), e);
        }
    }
}
