package com.marketmono.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market-data.strata-stock-prices")
@Data
public class StrataStockPriceCollectionProperties {

    private boolean enabled = false;
    private long intervalMs = 60_000L;
    private long initialDelayMs = 10_000L;
    private long lockAtMostForMs = 120_000L;
    private int staleAfterSeconds = 90;
}
