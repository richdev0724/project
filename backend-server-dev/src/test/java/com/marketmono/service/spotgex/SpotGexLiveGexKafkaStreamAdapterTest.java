package com.marketmono.service.spotgex;

import com.marketmono.config.SpotGexCollectionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpotGexLiveGexKafkaStreamAdapterTest {

    private SpotGexLiveGexProtobufDecoder decoder;
    private SimpleMeterRegistry meterRegistry;
    private KafkaListenerEndpointRegistry listenerRegistry;
    private MessageListenerContainer listenerContainer;
    private SpotGexLiveGexKafkaStreamAdapter adapter;

    @BeforeEach
    void setUp() {
        decoder = mock(SpotGexLiveGexProtobufDecoder.class);
        meterRegistry = new SimpleMeterRegistry();
        listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
        listenerContainer = mock(MessageListenerContainer.class);
        when(listenerRegistry.getListenerContainer(SpotGexLiveGexKafkaStreamAdapter.LISTENER_ID))
            .thenReturn(listenerContainer);
        adapter = new SpotGexLiveGexKafkaStreamAdapter(decoder, listenerRegistry, meterRegistry);
    }

    @Test
    void shouldStartManualKafkaListenerWhenHandlerIsRegistered() {
        when(listenerContainer.isRunning()).thenReturn(false);

        adapter.onEvent(event -> {});

        verify(listenerContainer).start();
    }

    @Test
    void shouldNotRestartManualKafkaListenerWhenAlreadyRunning() {
        when(listenerContainer.isRunning()).thenReturn(true);

        adapter.onEvent(event -> {});

        verify(listenerContainer, never()).start();
    }

    @Test
    void shouldReportConnectedWhenKafkaListenerIsRunningBeforeFirstRecord() {
        when(listenerContainer.isRunning()).thenReturn(true);

        adapter.onEvent(event -> {});
        adapter.subscribe("SPY");

        SpotGexStreamHealth health = adapter.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.activeTickerCount()).isEqualTo(1);
        assertThat(health.subscribedTickers()).containsExactly("SPY");
    }

    @Test
    void shouldFilterRecordKeyAndDesiredTickersBeforeDispatchingEvents() {
        SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord spyRecord = decodedRecord("SPY");
        SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord qqqRecord = decodedRecord("QQQ");
        when(decoder.decode(new byte[] {1})).thenReturn(Optional.of(spyRecord));
        when(decoder.decode(new byte[] {2})).thenReturn(Optional.of(qqqRecord));
        AtomicReference<SpotGexStreamEvent> received = new AtomicReference<>();
        Acknowledgment ack = mock(Acknowledgment.class);

        adapter.subscribe("SPY");
        adapter.onEvent(received::set);
        adapter.consume(List.of(
            record("gex-strike-v2", new byte[] {9}),
            record("gex-strike-expiry-v2", new byte[] {2}),
            record("gex-strike-expiry-v2", new byte[] {1})
        ), ack);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().ticker()).isEqualTo("SPY");
        verify(decoder, never()).decode(new byte[] {9});
        verify(ack).acknowledge();
        assertThat(meterRegistry.get("spot_gex.live_gex.records.received").counter().count()).isEqualTo(3.0d);
        assertThat(meterRegistry.get("spot_gex.live_gex.records.dispatched").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.live_gex.records.ignored_ticker").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.live_gex.records.decoded")
            .tag("charm_oi", "present")
            .counter()
            .count()).isEqualTo(2.0d);
    }

    @Test
    void shouldNotAcknowledgeWhenCanonicalHandoffFails() {
        when(decoder.decode(new byte[] {1})).thenReturn(Optional.of(decodedRecord("SPY")));
        Acknowledgment ack = mock(Acknowledgment.class);

        adapter.subscribe("SPY");
        adapter.onEvent(event -> {
            throw new IllegalStateException("handoff failed");
        });
        adapter.consume(List.of(record("gex-strike-expiry-v2", new byte[] {1})), ack);

        verify(ack, never()).acknowledge();
        assertThat(meterRegistry.get("spot_gex.live_gex.records.handoff_failed").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void shouldCountDecodedRecordsWithMissingCharmOi() {
        when(decoder.decode(new byte[] {1})).thenReturn(Optional.of(decodedRecordWithoutCharm("SPY")));
        Acknowledgment ack = mock(Acknowledgment.class);

        adapter.subscribe("SPY");
        adapter.onEvent(event -> {});
        adapter.consume(List.of(record("gex-strike-expiry-v2", new byte[] {1})), ack);

        assertThat(meterRegistry.get("spot_gex.live_gex.records.decoded")
            .tag("charm_oi", "missing")
            .counter()
            .count()).isEqualTo(1.0d);
        verify(ack).acknowledge();
    }

    private static ConsumerRecord<String, byte[]> record(String key, byte[] value) {
        return new ConsumerRecord<>("live-gex", 0, 42L, key, value);
    }

    private static SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord decodedRecord(String ticker) {
        return new SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord(
            ticker,
            1_781_271_004_000L,
            new BigDecimal("100"),
            new BigDecimal("117.16"),
            LocalDate.of(2026, 6, 12),
            java.util.Map.of(5, new BigDecimal("1"), 6, new BigDecimal("2"),
                7, new BigDecimal("3"), 8, new BigDecimal("4"))
        );
    }

    private static SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord decodedRecordWithoutCharm(String ticker) {
        return new SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord(
            ticker,
            1_781_271_004_000L,
            new BigDecimal("100"),
            new BigDecimal("117.16"),
            LocalDate.of(2026, 6, 12),
            java.util.Map.of(5, new BigDecimal("1"), 6, new BigDecimal("2"))
        );
    }
}
