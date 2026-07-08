package com.marketmono.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TickerDetailsResponseTest {

    @Test
    void shouldUseProviderNeutralStubNote() {
        TickerDetailsResponse response = TickerDetailsResponse.createStub("SPY");

        assertThat(response.note()).isEqualTo("Company metadata is not available for this ticker.");
        assertThat(response.note()).doesNotContain("UW");
    }
}
