package com.marketmono.controller;

import com.marketmono.dto.StrataStockPriceLatestDto;
import com.marketmono.dto.StrataStockPriceLatestResponse;
import com.marketmono.dto.StrataStockPriceMissingDto;
import com.marketmono.service.StrataStockPriceLatestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StrataStockPriceControllerTest {

    @Mock
    private StrataStockPriceLatestService latestService;

    @Test
    void shouldReturnLatestPricesAndMissingTickersForPartialBatch() throws Exception {
        when(latestService.getLatest(List.of("spxw", "VIXW", "MSFT"))).thenReturn(
            new StrataStockPriceLatestResponse(
                List.of(
                    price("SPXW", "SPX", "7499.36", "7440.43", "0.79202", false, 12),
                    price("VIXW", "VIX", "16.12", "15.99", "0.81201", true, 120)
                ),
                List.of(new StrataStockPriceMissingDto("MSFT", "MSFT", "not_persisted"))
            )
        );

        mockMvc()
            .perform(get("/api/v1/strata/stock-prices/latest")
                .param("tickers", " spxw, VIXW, , MSFT "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prices.length()").value(2))
            .andExpect(jsonPath("$.prices[0].displayTicker").value("SPXW"))
            .andExpect(jsonPath("$.prices[0].sourceTicker").value("SPX"))
            .andExpect(jsonPath("$.prices[0].closePrice").value(7499.36))
            .andExpect(jsonPath("$.prices[0].stale").value(false))
            .andExpect(jsonPath("$.prices[0].ageSeconds").value(12))
            .andExpect(jsonPath("$.prices[1].displayTicker").value("VIXW"))
            .andExpect(jsonPath("$.prices[1].stale").value(true))
            .andExpect(jsonPath("$.prices[1].ageSeconds").value(120))
            .andExpect(jsonPath("$.missing.length()").value(1))
            .andExpect(jsonPath("$.missing[0].displayTicker").value("MSFT"))
            .andExpect(jsonPath("$.missing[0].reason").value("not_persisted"));

        verify(latestService).getLatest(List.of("spxw", "VIXW", "MSFT"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new StrataStockPriceController(latestService))
            .build();
    }

    private static StrataStockPriceLatestDto price(
        String displayTicker,
        String sourceTicker,
        String closePrice,
        String prevClosePrice,
        String changePercent,
        boolean stale,
        long ageSeconds
    ) {
        return new StrataStockPriceLatestDto(
            displayTicker,
            sourceTicker,
            new BigDecimal(closePrice),
            new BigDecimal(prevClosePrice),
            new BigDecimal(changePercent),
            null,
            null,
            null,
            10_000L,
            12_000L,
            "market",
            Instant.parse("2026-07-01T09:29:58Z"),
            Instant.parse("2026-07-01T09:30:00Z"),
            stale,
            ageSeconds
        );
    }
}
