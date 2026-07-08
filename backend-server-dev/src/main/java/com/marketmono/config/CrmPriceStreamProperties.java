package com.marketmono.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Live price streaming for tickers saved in CRM filters. When enabled, those
 * tickers are served from the Unusual Whales {@code price:<TICKER>} websocket
 * (stored + relayed over STOMP) and are excluded from the Strata stock-state
 * REST collector. SPX/SPXW/VIX/VIXW always keep the REST pipeline.
 */
@Configuration
@ConfigurationProperties(prefix = "market-data.crm-price-stream")
@Data
public class CrmPriceStreamProperties {

    private boolean enabled = false;
    /** How often the CRM ticker set is re-read and UW subscriptions reconciled. */
    private long tickerRefreshMs = 60_000L;
    /** How often the latest in-memory ticks are flushed to crm_price_snapshots. */
    private long persistIntervalMs = 5_000L;
    /** Minimum gap between STOMP broadcasts per ticker (drops intermediate ticks). */
    private long broadcastThrottleMs = 1_000L;
    private long initialDelayMs = 15_000L;
    /** Baselines older than this many days are purged on each collection. */
    private int baselineRetentionDays = 4;
}
