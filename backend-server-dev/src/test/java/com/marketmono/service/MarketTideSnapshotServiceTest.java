package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.MarketTideResponse;
import com.marketmono.entity.MarketTideSnapshot;
import com.marketmono.repository.DashboardOhlcSnapshotRepository;
import com.marketmono.repository.MarketTideSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketTideSnapshotServiceTest {

    @Mock
    private MarketTideSnapshotRepository marketTideSnapshotRepository;

    @Mock
    private DashboardOhlcSnapshotRepository dashboardOhlcSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private MarketTideSnapshotService marketTideSnapshotService;

    @Test
    void shouldServeFreshSnapshotFromDatabaseWithoutUpstreamCalls() {
        String series = """
            [{
                "timestamp": 1751895000000,
                "date": "2026-07-05",
                "call_premium": 1250000.5,
                "put_premium": -830000.25,
                "net_volume": 42000,
                "spy_close": 618.42,
                "original_timestamp": "2026-07-05T13:30:00Z"
            }]
            """;
        MarketTideSnapshot snapshot = MarketTideSnapshot.builder()
            .tradingDate(LocalDate.of(2026, 7, 5))
            .series(series)
            .fetchedAt(Instant.now())
            .build();

        when(marketTideSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.of(snapshot));

        MarketTideResponse response = marketTideSnapshotService.getMarketTideFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).timestamp()).isEqualTo(1751895000000L);
        assertThat(response.data().get(0).callPremium()).isEqualByComparingTo(BigDecimal.valueOf(1250000.5));
        assertThat(response.data().get(0).putPremium()).isEqualByComparingTo(BigDecimal.valueOf(-830000.25));
        assertThat(response.data().get(0).netVolume()).isEqualTo(42000);
        assertThat(response.data().get(0).spyClose()).isEqualByComparingTo(BigDecimal.valueOf(618.42));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldReturnEmptySeriesWithoutUpstreamFallbackWhenSnapshotMissing() {
        when(marketTideSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.empty());

        MarketTideResponse response = marketTideSnapshotService.getMarketTideFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEmpty();
        verifyNoInteractions(unusualWhalesClient);
        verifyNoInteractions(dashboardOhlcSnapshotRepository);
    }

    @Test
    void shouldTreatStaleSnapshotAsMissing() {
        MarketTideSnapshot staleSnapshot = MarketTideSnapshot.builder()
            .tradingDate(LocalDate.now().minusDays(10))
            .series("[]")
            .fetchedAt(Instant.now().minus(Duration.ofDays(10)))
            .build();

        when(marketTideSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.of(staleSnapshot));

        MarketTideResponse response = marketTideSnapshotService.getMarketTideFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEmpty();
        verifyNoInteractions(unusualWhalesClient);
    }
}
