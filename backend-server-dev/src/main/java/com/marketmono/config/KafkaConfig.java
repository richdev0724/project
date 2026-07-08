package com.marketmono.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration properties for Kafka topic names and consumer tuning.
 * Kafka consumers are only activated when spring.kafka.enabled=true.
 */
@Configuration
@ConfigurationProperties(prefix = "kafka")
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@Getter
@Setter
@Slf4j
public class KafkaConfig {

    private Topics topics = new Topics();
    private Consumer consumer = new Consumer();
    private DeadLetter deadLetter = new DeadLetter();

    @PostConstruct
    public void logConfig() {
        log.info("[KAFKA] Kafka is ENABLED — topics: optionTrades={}, flowAlerts={}, liveGex={}",
            topics.getOptionTrades(), topics.getFlowAlerts(), topics.getLiveGex());
        log.info("[KAFKA] Consumer config — batchSize={}, batchTimeoutMs={}",
            consumer.getBatchSize(), consumer.getBatchTimeoutMs());
        log.info("[KAFKA] Dead-letter config — optionTradesTopic={}, retryAttempts={}, retryBackoffMs={}",
            deadLetter.getOptionTradesTopic(), deadLetter.getRetryAttempts(), deadLetter.getRetryBackoffMs());
    }

    @Getter
    @Setter
    public static class Topics {
        private String optionTrades = "all-option-trades";
        private String flowAlerts = "flow-alerts";
        private String liveGex = "live-gex";
    }

    @Getter
    @Setter
    public static class Consumer {
        private int batchSize = 200;
        private long batchTimeoutMs = 2000;
    }

    @Getter
    @Setter
    public static class DeadLetter {
        private String optionTradesTopic = "all-option-trades-dlt";
        private int retryAttempts = 3;
        private long retryBackoffMs = 1000;
    }
}
