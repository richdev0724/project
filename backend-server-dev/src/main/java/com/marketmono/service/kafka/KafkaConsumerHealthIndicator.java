package com.marketmono.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Kafka consumer status.
 * Reports whether Kafka listeners are running and consuming.
 */
@Component("kafkaConsumerHealth")
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerHealthIndicator implements HealthIndicator {

    private final KafkaListenerEndpointRegistry registry;

    @Override
    public Health health() {
        var containers = registry.getListenerContainers();
        if (containers.isEmpty()) {
            log.warn("[KAFKA] Health check: no Kafka listener containers registered");
            return Health.unknown().withDetail("reason", "No Kafka listener containers registered").build();
        }

        long running = containers.stream().filter(MessageListenerContainer::isRunning).count();
        long total = containers.size();

        if (running == total) {
            log.debug("[KAFKA] Health check: all {}/{} listeners running", running, total);
            return Health.up()
                .withDetail("listeners", total)
                .withDetail("running", running)
                .build();
        } else if (running > 0) {
            log.warn("[KAFKA] Health check: only {}/{} listeners running", running, total);
            return Health.up()
                .withDetail("listeners", total)
                .withDetail("running", running)
                .withDetail("warning", "Some listeners are not running")
                .build();
        } else {
            log.error("[KAFKA] Health check: ALL listeners are DOWN (0/{})", total);
            return Health.down()
                .withDetail("listeners", total)
                .withDetail("running", 0)
                .build();
        }
    }
}
