package com.marketmono.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.option-trades")
@Data
public class OptionTradesProperties {

    private Recent recent = new Recent();
    private Guard guard = new Guard();
    private MarketDay marketDay = new MarketDay();
    private Cache cache = new Cache();

    @Data
    public static class Recent {
        private Duration fallbackWindow = Duration.ofMinutes(30);
        private BigDecimal fallbackMinPremium = BigDecimal.valueOf(50_000);
        private long fallbackMinVolume = 100L;
    }

    @Data
    public static class Guard {
        private boolean enabled = true;
        private int maxConcurrentExpensiveQueries = 2;
        private int retryAfterSeconds = 5;
    }

    @Data
    public static class MarketDay {
        private int fallbackLookbackDays = 10;
    }

    @Data
    public static class Cache {
        private int maxEntries = 512;
        private Duration freshTtl = Duration.ofSeconds(15);
        private Duration staleTtl = Duration.ofMinutes(2);
        private Duration negativeCooldownTtl = Duration.ofSeconds(5);
    }
}
