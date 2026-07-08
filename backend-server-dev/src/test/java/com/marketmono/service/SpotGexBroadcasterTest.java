package com.marketmono.service;

import com.marketmono.dto.SpotGexTickerUpdateDto;
import com.marketmono.entity.SpotGexExposure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpotGexBroadcasterTest {

    @Mock
    private WebSocketRelayPublisher webSocketRelayPublisher;

    @InjectMocks
    private SpotGexBroadcaster spotGexBroadcaster;

    @Test
    void shouldBroadcastTopGammaSummaryForTicker() {
        Instant collectedAt = Instant.parse("2026-04-06T14:31:00Z");
        SpotGexExposure lowGamma = exposure("SPY", "2026-04-17", "500.0000", "3", "1");
        SpotGexExposure highGamma = exposure("SPY", "2026-04-17", "505.0000", "15", "1");

        spotGexBroadcaster.broadcastTickerSnapshot("SPY", collectedAt, List.of(lowGamma, highGamma));

        ArgumentCaptor<SpotGexTickerUpdateDto> payloadCaptor = ArgumentCaptor.forClass(SpotGexTickerUpdateDto.class);
        verify(webSocketRelayPublisher).publish(eq("/topic/spot-gex/SPY"), payloadCaptor.capture());

        SpotGexTickerUpdateDto payload = payloadCaptor.getValue();
        assertThat(payload.rowCount()).isEqualTo(2);
        assertThat(payload.topGammaStrikes()).hasSize(2);
        assertThat(payload.topGammaStrikes().getFirst().strike()).isEqualByComparingTo("505.0000");
        assertThat(payload.topGammaStrikes().getFirst().netGamma()).isEqualByComparingTo("14");
    }

    private SpotGexExposure exposure(String ticker, String expiry, String strike, String callGamma, String putGamma) {
        return SpotGexExposure.builder()
            .ticker(ticker)
            .expiry(LocalDate.parse(expiry))
            .strike(new BigDecimal(strike))
            .collectedAt(Instant.parse("2026-04-06T14:31:00Z"))
            .price(new BigDecimal("502.1200"))
            .callGammaOi(new BigDecimal(callGamma))
            .putGammaOi(new BigDecimal(putGamma))
            .build();
    }
}