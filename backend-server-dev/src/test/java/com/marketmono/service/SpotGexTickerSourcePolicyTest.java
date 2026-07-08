package com.marketmono.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexTickerSourcePolicyTest {

    private final SpotGexTickerSourcePolicy policy = new SpotGexTickerSourcePolicy(new StrataTickerFamilyResolver());

    @Test
    void shouldClassifyIndexFamiliesAsRestOnlyAndExcludeThemFromStreamTickers() {
        assertThat(policy.isRestOnlyTicker("spx")).isTrue();
        assertThat(policy.isRestOnlyTicker("SPXW")).isTrue();
        assertThat(policy.isRestOnlyTicker("vix")).isTrue();
        assertThat(policy.isRestOnlyTicker("VIXW")).isTrue();
        assertThat(policy.isRestOnlyTicker("SPY")).isFalse();

        assertThat(policy.streamEligibleTickers(List.of("SPY", "SPX", "spxw", "VIX", "QQQ")))
            .containsExactly("SPY", "QQQ");
    }

    @Test
    void shouldExpandConfiguredRestOnlyFamiliesToProviderCollectionRoots() {
        assertThat(policy.collectionRootsForConfiguredTickers(List.of("SPXW")))
            .containsExactly("SPXW", "SPX");
        assertThat(policy.collectionRootsForConfiguredTickers(List.of("VIXW")))
            .containsExactly("VIXW", "VIX");
        assertThat(policy.collectionRootsForConfiguredTickers(List.of("SPX", "VIX", "SPY")))
            .containsExactly("SPXW", "SPX", "VIXW", "VIX");
    }
}
