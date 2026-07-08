package com.marketmono.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrataTickerPolicyTest {

    @Test
    void shouldResolveDisplayTickerToConfiguredSourceTicker() {
        assertThat(StrataTickerPolicy.resolve("spxw"))
            .extracting(StrataTickerMapping::displayTicker, StrataTickerMapping::sourceTicker)
            .containsExactly("SPXW", "SPX");

        assertThat(StrataTickerPolicy.resolve("VIXW"))
            .extracting(StrataTickerMapping::displayTicker, StrataTickerMapping::sourceTicker)
            .containsExactly("VIXW", "VIX");

        assertThat(StrataTickerPolicy.resolve(" spy "))
            .extracting(StrataTickerMapping::displayTicker, StrataTickerMapping::sourceTicker)
            .containsExactly("SPY", "SPY");
    }
}
