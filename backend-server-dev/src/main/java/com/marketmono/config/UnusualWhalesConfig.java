package com.marketmono.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "unusual-whales")
@Getter
@Setter
public class UnusualWhalesConfig {
    
    private Api api = new Api();
    private WebSocketConfig websocket = new WebSocketConfig();
    private Flow flow = new Flow();
    
    @Getter
    @Setter
    public static class Api {
        private String baseUrl;
        private String apiKey;
        private boolean restEnabled = true;
        private int maxConcurrentRequests = 12;
        private Endpoints endpoints = new Endpoints();
        private RateLimit rateLimit = new RateLimit();
        private Timeout timeout = new Timeout();
        private Retry retry = new Retry();
        private Reporting reporting = new Reporting();
    }
    
    @Getter
    @Setter
    public static class Endpoints {
        private boolean congressTrades = false;
        private boolean politicianTrades = false;
        private boolean politicianPortfolios = false;
        private boolean politiciansList = false;
        private boolean flowAlerts = false;
        private boolean marketTide = false;
        private boolean topNetImpact = false;
        private boolean spyOhlc = false;
        private boolean tickerTide = false;
        private boolean tickerOhlc = false;
        private boolean stockState = false;
        private boolean stockVolumePriceLevels = false;
        private boolean news = false;
        private boolean greekExposure = false;
        private boolean earnings = true;  // Enable earnings endpoints by default
        private boolean darkpoolRecent = false;
        private boolean optionContracts = true;
        private boolean spotExposure = false;
    }
    
    @Getter
    @Setter
    public static class RateLimit {
        private int dailyLimit = 300000;
        private int targetPerMinute = 490;
        private int hardCapPerMinute = 500;
    }
    
    @Getter
    @Setter
    public static class Timeout {
        private int connect = 5000;
        private int read = 30000;
        private int permitWait = 1000;
    }
    
    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 0;
        private int backoffMultiplier = 2;
    }

    @Getter
    @Setter
    public static class Reporting {
        private boolean enabled = false;
        private String adminApiBaseUrl;
        private String internalSecret;
        private int connectTimeout = 1000;
        private int readTimeout = 2000;
        private int maxAttempts = 1;
    }
    
    @Getter
    @Setter
    public static class Flow {
        private String primarySource;
        private boolean fallbackToRest;
        private Sync sync = new Sync();
    }
    
    @Getter
    @Setter
    public static class WebSocketConfig {
        private boolean enabled;
        private String url;
        private int reconnectIntervalSeconds;
        private int maxReconnectAttempts;
        private int pingIntervalSeconds;
        private int messageBufferSize;
        private Batch batch = new Batch();
        private News news = new News();
    }
    
    @Getter
    @Setter
    public static class Batch {
        private int maxSize = 50;  // Default: 50 alerts per batch
        private long maxBytes = 1_048_576L;  // Default: 1MB per batch
        private long flushIntervalMs = 2000L;  // Default: 2 seconds flush interval
    }

    @Getter
    @Setter
    public static class News {
        private boolean enabled = true;
    }
    
    @Getter
    @Setter
    public static class Sync {
        private boolean enabled;
        private int intervalSeconds;
        private int batchSize;
        private int retentionDays;
        private int initialDelaySeconds;
    }
}
