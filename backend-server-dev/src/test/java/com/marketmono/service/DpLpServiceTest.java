package com.marketmono.service;

import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.entity.DpLpTrade;
import com.marketmono.entity.VenueType;
import com.marketmono.repository.DpLpAggregateRepository;
import com.marketmono.repository.DpLpTradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpLpServiceTest {

    @Mock
    private DpLpTradeRepository tradeRepository;

    @Mock
    private DpLpAggregateRepository aggregateRepository;

    @Mock
    private DarkPoolBroadcaster darkPoolBroadcaster;

    @InjectMocks
    private DpLpService service;

    @Test
    void shouldReturnDailyDarkPoolTradesSortedByComputedPremium() {
        LocalDate tradeDate = LocalDate.of(2026, 5, 12);
        OffsetDateTime start = OffsetDateTime.of(2026, 5, 12, 0, 0, 0, 0, ZoneOffset.ofHours(-4));
        OffsetDateTime end = OffsetDateTime.of(2026, 5, 13, 0, 0, 0, 0, ZoneOffset.ofHours(-4));

        DpLpTrade spyEarly = trade(1L, "SPY", "500.00", 10, "2026-05-12T13:00:00Z");
        DpLpTrade spyLate = trade(2L, "SPY", "500.00", 20, "2026-05-12T14:00:00Z");
        DpLpTrade qqq = trade(3L, "QQQ", "100.00", 100, "2026-05-12T15:00:00Z");
        when(tradeRepository.findByVenueTypeAndTsBetweenOrderByTsAsc(
            eq(VenueType.DARK),
            eq(start),
            eq(end)
        )).thenReturn(List.of(qqq, spyEarly, spyLate));

        var result = service.getTopDarkPoolTradesForDay(tradeDate, 1);

        assertThat(result.data()).hasSize(1);
        var topTrade = result.data().getFirst();
        assertThat(topTrade.ticker()).isEqualTo("SPY");
        assertThat(topTrade.price()).isEqualByComparingTo("500.00");
        assertThat(topTrade.size()).isEqualTo(30);
        assertThat(topTrade.volume()).isEqualTo(30);
        assertThat(topTrade.premium()).isEqualByComparingTo("15000.00");
        assertThat(topTrade.trackingId()).isEqualTo(2L);
        assertThat(topTrade.executedAt()).isEqualTo("2026-05-12T14:00Z");
        assertThat(topTrade.canceled()).isFalse();
        assertThat(result.constituentsByTrackingId())
            .containsOnlyKeys("2");
        assertThat(result.constituentsByTrackingId().get("2"))
            .extracting(DarkPoolRecentTradeDto::trackingId)
            .containsExactly(1L, 2L);

        verify(tradeRepository).findByVenueTypeAndTsBetweenOrderByTsAsc(
            VenueType.DARK,
            start,
            end
        );
    }

    private static DpLpTrade trade(Long id, String ticker, String price, int size, String executedAt) {
        return DpLpTrade.builder()
            .id(id)
            .ticker(ticker)
            .venueType(VenueType.DARK)
            .price(new BigDecimal(price))
            .size(size)
            .ts(OffsetDateTime.parse(executedAt))
            .venue("D")
            .ingestSource("OPTIONS_FEED_WS")
            .build();
    }
}
