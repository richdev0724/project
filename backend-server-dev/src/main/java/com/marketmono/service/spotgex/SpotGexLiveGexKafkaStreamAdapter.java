package com.marketmono.service.spotgex;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

@Component
@ConditionalOnExpression("'${spot-gex.collection.stream-transport:websocket}' == 'live-gex' && '${spring.kafka.enabled:false}' == 'true'")
@Lazy(false)
@Slf4j
public class SpotGexLiveGexKafkaStreamAdapter implements SpotGexStreamAdapter {

    public static final String LISTENER_ID = "spotGexLiveGexKafkaStream";
    private static final String EXPECTED_RECORD_KEY = "gex-strike-expiry-v2";
    private static final String SOURCE = SpotGexLiveGexProtobufDecoder.SOURCE_OPTIONS_FEED_LIVE_GEX;

    private final SpotGexLiveGexProtobufDecoder decoder;
    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final Set<String> desiredTickers = new ConcurrentSkipListSet<>();
    private final Counter receivedCounter;
    private final Counter ignoredKeyCounter;
    private final Counter decodeSkippedCounter;
    private final Counter decodedCharmOiPresentCounter;
    private final Counter decodedCharmOiMissingCounter;
    private final Counter ignoredTickerCounter;
    private final Counter dispatchedCounter;
    private final Counter handoffFailedCounter;
    private volatile Consumer<SpotGexStreamEvent> eventHandler;
    private volatile Instant lastRecordAt;

    public SpotGexLiveGexKafkaStreamAdapter(
            SpotGexLiveGexProtobufDecoder decoder,
            KafkaListenerEndpointRegistry listenerRegistry,
            MeterRegistry meterRegistry) {
        this.decoder = decoder;
        this.listenerRegistry = listenerRegistry;
        this.receivedCounter = meterRegistry.counter("spot_gex.live_gex.records.received");
        this.ignoredKeyCounter = meterRegistry.counter("spot_gex.live_gex.records.ignored_key");
        this.decodeSkippedCounter = meterRegistry.counter("spot_gex.live_gex.records.decode_skipped");
        this.decodedCharmOiPresentCounter = meterRegistry.counter(
            "spot_gex.live_gex.records.decoded", "charm_oi", "present");
        this.decodedCharmOiMissingCounter = meterRegistry.counter(
            "spot_gex.live_gex.records.decoded", "charm_oi", "missing");
        this.ignoredTickerCounter = meterRegistry.counter("spot_gex.live_gex.records.ignored_ticker");
        this.dispatchedCounter = meterRegistry.counter("spot_gex.live_gex.records.dispatched");
        this.handoffFailedCounter = meterRegistry.counter("spot_gex.live_gex.records.handoff_failed");
    }

    @KafkaListener(
        id = LISTENER_ID,
        topics = "${kafka.topics.live-gex:live-gex}",
        groupId = "${kafka.consumers.spot-gex.group-id:marketmono-spot-gex-stream}",
        containerFactory = "kafkaListenerContainerFactory",
        autoStartup = "false"
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        receivedCounter.increment(records.size());
        lastRecordAt = Instant.now();

        for (ConsumerRecord<String, byte[]> record : records) {
            if (!EXPECTED_RECORD_KEY.equals(record.key())) {
                ignoredKeyCounter.increment();
                continue;
            }

            SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord decoded = decoder.decode(record.value()).orElse(null);
            if (decoded == null) {
                decodeSkippedCounter.increment();
                continue;
            }
            recordDecodedCharmPresence(decoded);

            if (!desiredTickers.contains(decoded.ticker())) {
                ignoredTickerCounter.increment();
                continue;
            }

            Consumer<SpotGexStreamEvent> handler = eventHandler;
            if (handler == null) {
                continue;
            }

            try {
                handler.accept(decoded.toStreamEvent(Instant.now()));
                dispatchedCounter.increment();
            } catch (RuntimeException exception) {
                handoffFailedCounter.increment();
                log.warn("[SPOT-GEX] live-gex canonical handoff failed at topic={}, partition={}, offset={}: {}",
                    record.topic(), record.partition(), record.offset(), exception.getMessage(), exception);
                return;
            }
        }

        ack.acknowledge();
    }

    @Override
    public void subscribe(String ticker) {
        desiredTickers.add(normalizeTicker(ticker));
    }

    @Override
    public void unsubscribe(String ticker) {
        desiredTickers.remove(normalizeTicker(ticker));
    }

    @Override
    public void onEvent(Consumer<SpotGexStreamEvent> handler) {
        this.eventHandler = handler;
        startListener();
    }

    @Override
    public SpotGexStreamHealth health() {
        List<String> tickerSnapshot = List.copyOf(desiredTickers);
        return new SpotGexStreamHealth(
            SOURCE,
            lastRecordAt != null || listenerRunning(),
            tickerSnapshot.size(),
            tickerSnapshot
        );
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private void startListener() {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(LISTENER_ID);
        if (container == null) {
            log.warn("[SPOT-GEX] live-gex Kafka listener container '{}' was not found", LISTENER_ID);
            return;
        }
        if (!container.isRunning()) {
            container.start();
            log.info("[SPOT-GEX] live-gex Kafka listener started");
        }
    }

    private boolean listenerRunning() {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(LISTENER_ID);
        return container != null && container.isRunning();
    }

    private void recordDecodedCharmPresence(SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord decoded) {
        if (decoded.numericFields().get(7) != null && decoded.numericFields().get(8) != null) {
            decodedCharmOiPresentCounter.increment();
        } else {
            decodedCharmOiMissingCounter.increment();
        }
    }
}
