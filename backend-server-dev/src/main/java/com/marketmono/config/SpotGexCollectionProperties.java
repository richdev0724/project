package com.marketmono.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for scheduled spot GEX collection.
 */
@Configuration
@ConfigurationProperties(prefix = "spot-gex.collection")
@Data
public class SpotGexCollectionProperties {

    private boolean enabled = false;
    private long intervalMs = 60_000L;
    private long initialDelayMs = 30_000L;
    private long restOnlyIntervalMs = 180_000L;
    private long interTickerDelayMs = 100L;
    private long interPageDelayMs = 50L;
    private boolean abortWhenUwBudgetAboveTarget = true;
    private int dailyProjectionWarningThreshold = 120_000;
    private int dailyProjectionStopThreshold = 150_000;
    private int maxTickersPerCycle = 4;
    private long coreTickerIntervalMs = 60_000L;
    private long extendedTickerIntervalMs = 300_000L;
    private int pageSize = 500;
    private int retentionDays = 90;
    private SourceMode source = SourceMode.LEGACY;
    private StreamTransport streamTransport = StreamTransport.WEBSOCKET;
    private long streamStaleThresholdMs = 120_000L;
    private int streamMaxPendingEvents = 10_000;
    private boolean streamDeltaRestEnrichmentEnabled = true;
    private long liveFallbackCacheTtlMs = 45_000L;
    private String streamOwnerNodeId;
    private List<String> coreTickers = new ArrayList<>();
    private List<String> fallbackTickers = new ArrayList<>(List.of(
        "SPY", "QQQ", "IWM", "DIA", "AAPL", "AMZN", "META", "MSFT", "NVDA", "TSLA", "GOOG", "GOOGL"
    ));
    private MarketHours marketHours = new MarketHours();

    @Data
    public static class MarketHours {
        private String proxyTicker = "SPY";
        private String fallbackTimezone = "America/New_York";
    }

    public enum SourceMode {
        LEGACY,
        STREAM
    }

    public enum StreamTransport {
        WEBSOCKET,
        LIVE_GEX,
        FAKE
    }
}
