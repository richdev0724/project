package com.marketmono.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStatusResponseTest {

    @Test
    void shouldMapRegularSessionToOpen() {
        MarketStatusResponse response = MarketStatusResponse.fromMarketTime(
            "regular",
            "2026-04-06T19:15:00Z"
        );

        assertThat(response.exchanges().nasdaq()).isEqualTo("open");
        assertThat(response.exchanges().nyse()).isEqualTo("open");
        assertThat(response.marketTime()).isEqualTo("regular");
    }
}