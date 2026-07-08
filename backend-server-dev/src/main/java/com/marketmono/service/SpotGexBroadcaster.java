package com.marketmono.service;

import com.marketmono.dto.SpotGexTickerUpdateDto;
import com.marketmono.entity.SpotGexExposure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Broadcasts compact spot GEX snapshot summaries to WebSocket subscribers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpotGexBroadcaster {

    private static final int TOP_STRIKES_LIMIT = 10;

    private final WebSocketRelayPublisher webSocketRelayPublisher;

    @Async
    public void broadcastTickerSnapshot(String ticker, Instant collectedAt, List<SpotGexExposure> exposures) {
        if (exposures == null || exposures.isEmpty()) {
            return;
        }

        try {
            SpotGexTickerUpdateDto payload = new SpotGexTickerUpdateDto(
                ticker,
                collectedAt,
                exposures.size(),
                exposures.stream()
                    .sorted(Comparator.comparing(this::absoluteNetGamma).reversed())
                    .limit(TOP_STRIKES_LIMIT)
                    .map(exposure -> new SpotGexTickerUpdateDto.StrikeSummary(
                        exposure.getExpiry(),
                        exposure.getStrike(),
                        exposure.getPrice(),
                        exposure.getCallGammaOi(),
                        exposure.getPutGammaOi(),
                        netGamma(exposure)
                    ))
                    .toList()
            );

            webSocketRelayPublisher.publish("/topic/spot-gex/" + ticker, payload);
            log.debug("[SPOT-GEX] Broadcast {} spot GEX rows for {}", exposures.size(), ticker);
        } catch (Exception ex) {
            log.warn("[SPOT-GEX] Failed to broadcast spot GEX update for {}: {}", ticker, ex.getMessage(), ex);
        }
    }

    private BigDecimal absoluteNetGamma(SpotGexExposure exposure) {
        return netGamma(exposure).abs();
    }

    private BigDecimal netGamma(SpotGexExposure exposure) {
        BigDecimal callGamma = exposure.getCallGammaOi() != null ? exposure.getCallGammaOi() : BigDecimal.ZERO;
        BigDecimal putGamma = exposure.getPutGammaOi() != null ? exposure.getPutGammaOi() : BigDecimal.ZERO;
        return callGamma.subtract(putGamma);
    }
}