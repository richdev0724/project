package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.StockStateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketHoursServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    private MarketHoursService marketHoursService;

    @BeforeEach
    void setUp() {
        SpotGexCollectionProperties properties = new SpotGexCollectionProperties();
        properties.getMarketHours().setProxyTicker("SPY");
        properties.getMarketHours().setFallbackTimezone("America/New_York");
        marketHoursService = new MarketHoursService(unusualWhalesClient, properties);
    }

    @Test
    void shouldUseApiMarketTimeWhenAvailable() {
        when(unusualWhalesClient.getStockStateForScheduler("SPY")).thenReturn(
            new StockStateResponse(new StockStateResponse.StockStateData(
                "501.25",
                "499.00",
                "2026-04-06T14:31:00Z",
                100L,
                200L,
                "498.00",
                "502.00",
                "497.50",
                "market"
            ))
        );

        MarketHoursService.MarketStatus status = marketHoursService.getRegularMarketStatus();

        assertThat(status.open()).isTrue();
        assertThat(status.source()).isEqualTo("uw-stock-state");
        assertThat(status.marketTime()).isEqualTo("market");
    }

    @Test
    void shouldTreatRegularSessionAsOpen() {
        when(unusualWhalesClient.getStockStateForScheduler("SPY")).thenReturn(
            new StockStateResponse(new StockStateResponse.StockStateData(
                "501.25",
                "499.00",
                "2026-04-06T14:31:00Z",
                100L,
                200L,
                "498.00",
                "502.00",
                "497.50",
                "regular"
            ))
        );

        MarketHoursService.MarketStatus status = marketHoursService.getRegularMarketStatus();

        assertThat(status.open()).isTrue();
        assertThat(status.source()).isEqualTo("uw-stock-state");
        assertThat(status.marketTime()).isEqualTo("regular");
    }

    @Test
    void shouldReturnFalseForFallbackWeekendWindow() {
        ZonedDateTime saturdayNoon = ZonedDateTime.of(2026, 4, 4, 12, 0, 0, 0, ZoneId.of("America/New_York"));

        assertThat(marketHoursService.isFallbackMarketOpen(saturdayNoon)).isFalse();
    }

    @Test
    void shouldReturnTrueForFallbackWeekdayWindow() {
        ZonedDateTime mondayMorning = ZonedDateTime.of(2026, 4, 6, 10, 15, 0, 0, ZoneId.of("America/New_York"));

        assertThat(marketHoursService.isFallbackMarketOpen(mondayMorning)).isTrue();
    }
}