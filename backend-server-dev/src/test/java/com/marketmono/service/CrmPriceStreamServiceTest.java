package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.CrmPriceStreamProperties;
import com.marketmono.dto.CrmPriceStreamSnapshotDto;
import com.marketmono.dto.CrmPriceStreamTickDto;
import com.marketmono.dto.UwPriceTickDto;
import com.marketmono.entity.CrmPriceBaseline;
import com.marketmono.repository.CrmPriceBaselineRepository;
import com.marketmono.repository.CrmPriceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmPriceStreamServiceTest {

    @Mock
    private UnusualWhalesWebSocketClient webSocketClient;

    @Mock
    private StrataStockPriceTickerUniverseService tickerUniverseService;

    @Mock
    private CrmPriceSnapshotRepository snapshotRepository;

    @Mock
    private CrmPriceBaselineRepository baselineRepository;

    @Mock
    private WebSocketRelayPublisher relayPublisher;

    private CrmPriceStreamProperties properties;
    private CrmPriceStreamService service;

    @BeforeEach
    void setUp() {
        properties = new CrmPriceStreamProperties();
        properties.setEnabled(true);
        properties.setBroadcastThrottleMs(0);
        service = new CrmPriceStreamService(
            properties,
            webSocketClient,
            tickerUniverseService,
            snapshotRepository,
            baselineRepository,
            relayPublisher
        );
    }

    @Test
    void shouldSubscribeNewCrmTickersAndUnsubscribeRemovedOnes() {
        when(tickerUniverseService.getCrmPriceStreamTickers())
            .thenReturn(new LinkedHashSet<>(List.of("PLTR", "AAPL")));
        when(baselineRepository.findLatestPerTicker()).thenReturn(List.of());

        service.refreshSubscriptions();

        verify(webSocketClient).subscribePriceTicker("PLTR");
        verify(webSocketClient).subscribePriceTicker("AAPL");

        when(tickerUniverseService.getCrmPriceStreamTickers())
            .thenReturn(new LinkedHashSet<>(List.of("PLTR")));

        service.refreshSubscriptions();

        verify(webSocketClient).unsubscribePriceTicker("AAPL");
    }

    @Test
    void shouldBroadcastTickWithChangeAgainstBaselinePrevClose() {
        when(tickerUniverseService.getCrmPriceStreamTickers())
            .thenReturn(Set.of("PLTR"));
        when(baselineRepository.findLatestPerTicker()).thenReturn(List.of(
            baseline("PLTR", new BigDecimal("100.000000"))
        ));
        service.refreshSubscriptions();

        service.handleTick(new UwPriceTickDto("PLTR", new BigDecimal("105.50"), 1726670327692L, 6015555L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(relayPublisher).publish(eq("/topic/strata-prices/PLTR"), payloadCaptor.capture());
        CrmPriceStreamTickDto tick = (CrmPriceStreamTickDto) payloadCaptor.getValue();
        assertThat(tick.price()).isEqualByComparingTo("105.50");
        assertThat(tick.prevClose()).isEqualByComparingTo("100");
        assertThat(tick.change()).isEqualByComparingTo("5.50");
        assertThat(tick.changePercent()).isEqualByComparingTo("5.500000");
        assertThat(tick.time()).isEqualTo(1726670327692L);
        assertThat(tick.vol()).isEqualTo(6015555L);
    }

    @Test
    void shouldIgnoreTicksForUntrackedTickers() {
        service.handleTick(new UwPriceTickDto("PLTR", new BigDecimal("105.50"), 1726670327692L, null));

        verify(relayPublisher, never()).publish(any(), any());
    }

    @Test
    void shouldReturnUntrackedSnapshotForUnknownOrInvalidTickers() {
        assertThat(service.getSnapshot("PLTR").tracked()).isFalse();
        assertThat(service.getSnapshot("not a ticker!").tracked()).isFalse();
    }

    @Test
    void shouldReturnTrackedSnapshotWithLastTick() {
        when(tickerUniverseService.getCrmPriceStreamTickers())
            .thenReturn(Set.of("PLTR"));
        when(baselineRepository.findLatestPerTicker()).thenReturn(List.of());
        service.refreshSubscriptions();
        service.handleTick(new UwPriceTickDto("PLTR", new BigDecimal("105.50"), 1726670327692L, 100L));

        CrmPriceStreamSnapshotDto snapshot = service.getSnapshot("pltr");

        assertThat(snapshot.tracked()).isTrue();
        assertThat(snapshot.tick()).isNotNull();
        assertThat(snapshot.tick().price()).isEqualByComparingTo("105.50");
        // No baseline yet: % change withheld rather than guessed.
        assertThat(snapshot.tick().changePercent()).isNull();
    }

    @Test
    void shouldFlushDirtyTicksToRepository() {
        when(tickerUniverseService.getCrmPriceStreamTickers())
            .thenReturn(Set.of("PLTR"));
        when(baselineRepository.findLatestPerTicker()).thenReturn(List.of());
        service.refreshSubscriptions();
        service.handleTick(new UwPriceTickDto("PLTR", new BigDecimal("105.50"), 1726670327692L, 100L));

        service.flushDirtyTicks();
        service.flushDirtyTicks();

        verify(snapshotRepository).upsert(
            "PLTR",
            new BigDecimal("105.50"),
            Instant.ofEpochMilli(1726670327692L),
            100L
        );
    }

    private static CrmPriceBaseline baseline(String ticker, BigDecimal prevClose) {
        return CrmPriceBaseline.builder()
            .ticker(ticker)
            .snapshotDate(LocalDate.now())
            .prevClose(prevClose)
            .fetchedAt(Instant.now())
            .build();
    }
}
