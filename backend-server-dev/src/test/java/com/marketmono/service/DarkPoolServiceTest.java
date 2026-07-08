package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DarkPoolRecentResponseDto;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DarkPoolServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private DarkPoolService service;

    @Test
    void shouldReturnDarkFlowTopTradesSortedByAggregatedPremium() {
        LocalDate tradeDate = LocalDate.of(2026, 5, 11);
        var nvdaEarly = trade("NVDA", "219.44", 100, 100, "2026-05-11T18:00:00Z", "500000000", 1L, false);
        var nvdaLate = trade("NVDA", "219.44", 200, 200, "2026-05-11T19:00:00Z", "1000000000", 2L, false);
        var aapl = trade("AAPL", "292.68", 50, 50, "2026-05-11T18:30:00Z", "1200000000", 3L, false);
        var canceled = trade("CRCL", "131.76", 100, 100, "2026-05-11T19:30:00Z", "2000000000", 4L, true);
        var msft = trade("MSFT", "412.65", 25, 25, "2026-05-11T17:00:00Z", "90000000", 5L, false);

        when(unusualWhalesClient.getDarkPoolRecent(
            "2026-05-11", 200, 100_000_000L, null, null, null, null, null
        )).thenReturn(new DarkPoolRecentResponseDto(List.of(nvdaEarly, nvdaLate, aapl, canceled)));
        when(unusualWhalesClient.getDarkPoolRecent(
            "2026-05-11", 200, 50_000_000L, 100_000_000L, null, null, null, null
        )).thenReturn(new DarkPoolRecentResponseDto(List.of(msft)));

        var response = service.getTopDarkPoolTradesForDay(tradeDate, 2);

        assertThat(response.data()).hasSize(2);
        assertThat(response.data()).extracting(DarkPoolRecentTradeDto::ticker)
            .containsExactly("NVDA", "AAPL");
        assertThat(response.data().get(0).premium()).isEqualByComparingTo("1500000000");
        assertThat(response.data().get(0).size()).isEqualTo(300);
        assertThat(response.data().get(0).volume()).isEqualTo(300);
        assertThat(response.data().get(0).trackingId()).isEqualTo(2L);
        assertThat(response.constituentsByTrackingId()).containsOnlyKeys("2", "3");
        assertThat(response.constituentsByTrackingId().get("2"))
            .extracting(DarkPoolRecentTradeDto::trackingId)
            .containsExactly(1L, 2L);

        verify(unusualWhalesClient).getDarkPoolRecent(
            "2026-05-11", 200, 100_000_000L, null, null, null, null, null
        );
        verify(unusualWhalesClient).getDarkPoolRecent(
            "2026-05-11", 200, 50_000_000L, 100_000_000L, null, null, null, null
        );
    }

    private static DarkPoolRecentTradeDto trade(
        String ticker,
        String price,
        int size,
        int volume,
        String executedAt,
        String premium,
        Long trackingId,
        boolean canceled
    ) {
        return new DarkPoolRecentTradeDto(
            ticker,
            new BigDecimal(price),
            size,
            volume,
            executedAt,
            new BigDecimal(premium),
            null,
            null,
            null,
            null,
            "D",
            canceled,
            null,
            null,
            trackingId,
            null,
            null
        );
    }
}
