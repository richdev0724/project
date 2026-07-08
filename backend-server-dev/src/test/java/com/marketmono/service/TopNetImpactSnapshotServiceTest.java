package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.TopNetImpactResponse;
import com.marketmono.entity.TopNetImpactSnapshot;
import com.marketmono.repository.TopNetImpactSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopNetImpactSnapshotServiceTest {

    @Mock
    private TopNetImpactSnapshotRepository topNetImpactSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private TopNetImpactSnapshotService topNetImpactSnapshotService;

    @Test
    void shouldServeFreshSnapshotFromDatabaseWithoutUpstreamCalls() {
        TopNetImpactSnapshot snapshot = TopNetImpactSnapshot.builder()
            .tradingDate(LocalDate.of(2026, 7, 5))
            .bullish("""
                [{"ticker": "NVDA", "net_premium": 52000000.5},
                 {"ticker": "AAPL", "net_premium": 31000000.0}]
                """)
            .bearish("""
                [{"ticker": "TSLA", "net_premium": -47000000.25}]
                """)
            .fetchedAt(Instant.now())
            .build();

        when(topNetImpactSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.of(snapshot));

        TopNetImpactResponse response = topNetImpactSnapshotService.getTopNetImpactFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data().bullish()).hasSize(2);
        assertThat(response.data().bullish().get(0).ticker()).isEqualTo("NVDA");
        assertThat(response.data().bullish().get(0).netPremium()).isEqualTo(52000000.5);
        assertThat(response.data().bearish()).hasSize(1);
        assertThat(response.data().bearish().get(0).ticker()).isEqualTo("TSLA");
        assertThat(response.data().bearish().get(0).netPremium()).isEqualTo(-47000000.25);
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldReturnEmptyListsWithoutUpstreamFallbackWhenSnapshotMissing() {
        when(topNetImpactSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.empty());

        TopNetImpactResponse response = topNetImpactSnapshotService.getTopNetImpactFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data().bullish()).isEmpty();
        assertThat(response.data().bearish()).isEmpty();
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldTreatStaleSnapshotAsMissing() {
        TopNetImpactSnapshot staleSnapshot = TopNetImpactSnapshot.builder()
            .tradingDate(LocalDate.now().minusDays(10))
            .bullish("[]")
            .bearish("[]")
            .fetchedAt(Instant.now().minus(Duration.ofDays(10)))
            .build();

        when(topNetImpactSnapshotRepository.findTopByOrderByTradingDateDesc())
            .thenReturn(Optional.of(staleSnapshot));

        TopNetImpactResponse response = topNetImpactSnapshotService.getTopNetImpactFromDatabase();

        assertThat(response.success()).isTrue();
        assertThat(response.data().bullish()).isEmpty();
        assertThat(response.data().bearish()).isEmpty();
        verifyNoInteractions(unusualWhalesClient);
    }
}
