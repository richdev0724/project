package com.marketmono.controller;

import com.marketmono.dto.DarkPoolTopTradesResponseDto;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.service.DarkPoolService;
import com.marketmono.service.DpLpService;
import com.marketmono.service.OffLitTradeBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DarkPoolControllerTest {

    @Mock
    private DpLpService dpLpService;

    @Mock
    private DarkPoolService darkPoolService;

    @Mock
    private OffLitTradeBroadcaster offLitTradeBroadcaster;

    @InjectMocks
    private DarkPoolController controller;

    @Test
    void shouldReturnDbBackedTopDarkPoolTradesForRequestedDay() {
        LocalDate tradeDate = LocalDate.of(2026, 5, 12);
        DarkPoolRecentTradeDto trade = new DarkPoolRecentTradeDto(
            "SPY",
            new BigDecimal("500.00"),
            30,
            30,
            "2026-05-12T14:00Z",
            new BigDecimal("15000.00"),
            null,
            null,
            null,
            null,
            "D",
            false,
            null,
            null,
            2L,
            null,
            null
        );
        DarkPoolTopTradesResponseDto response = new DarkPoolTopTradesResponseDto(
            List.of(trade),
            Map.of("2", List.of(trade))
        );
        when(darkPoolService.getTopDarkPoolTradesForDay(tradeDate, 10)).thenReturn(response);

        var result = controller.getTopDarkPoolTradesForDay(tradeDate, 10);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        verify(darkPoolService).getTopDarkPoolTradesForDay(tradeDate, 10);
    }
}
