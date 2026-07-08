package com.marketmono.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for politician trades synchronization
 * Controls scheduled sync for Congress and Portfolio APIs
 */
@Configuration
@ConfigurationProperties(prefix = "politician.sync")
@Data
public class PoliticianSyncConfig {
    
    private boolean enabled = true;
    private CongressApiConfig congressApi = new CongressApiConfig();
    private PortfolioApiConfig portfolioApi = new PortfolioApiConfig();
    private int retentionDays = 90;
    private int maxPagesPerSync = 10;  // Prevent infinite loops (10 pages * 200 trades = 2,000 max trades per sync)
    
    @Data
    public static class CongressApiConfig {
        private boolean enabled = true;
        private int intervalMinutes = 30;
        private int initialDelayMinutes = 5;
        private int batchSize = 500;
    }
    
    @Data
    public static class PortfolioApiConfig {
        private boolean enabled = true;
        private int intervalMinutes = 30;
        private int initialDelayMinutes = 20;
        private int batchSize = 500;
    }
}
