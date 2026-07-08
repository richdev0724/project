package com.marketmono.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static com.marketmono.service.spotgex.SpotGexLiveGexKafkaStreamAdapter.LISTENER_ID;

/**
 * Starts Kafka listeners after the application is fully ready.
 * If bootstrap servers are unreachable, the app continues running
 * and retries every 60 seconds until connection is established.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerStartup {

    private final KafkaListenerEndpointRegistry registry;

    @EventListener(ApplicationReadyEvent.class)
    public void startListeners() {
        try {
            log.info("[KAFKA] Starting managed Kafka listeners...");
            startManagedListeners();
            log.info("[KAFKA] Managed Kafka listeners started successfully");
        } catch (Exception e) {
            log.error("[KAFKA] Failed to start Kafka listeners: {}. App continues without Kafka consumption. Will retry.",
                e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryStartListeners() {
        if (managedListenersRunning()) return;
        try {
            log.info("[KAFKA] Retrying Kafka listener startup...");
            startManagedListeners();
            log.info("[KAFKA] Kafka listeners started successfully on retry");
        } catch (Exception e) {
            log.warn("[KAFKA] Kafka listener startup retry failed: {}", e.getMessage());
        }
    }

    private void startManagedListeners() {
        managedListeners().stream()
            .filter(container -> !container.isRunning())
            .forEach(MessageListenerContainer::start);
    }

    private boolean managedListenersRunning() {
        return managedListeners().stream().allMatch(MessageListenerContainer::isRunning);
    }

    private Collection<MessageListenerContainer> managedListeners() {
        MessageListenerContainer spotGexListener = registry.getListenerContainer(LISTENER_ID);
        return registry.getListenerContainers().stream()
            .filter(container -> container != spotGexListener)
            .toList();
    }
}
