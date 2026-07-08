package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.StockStateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Locale;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Determines whether regular market hours are currently open for scheduled collection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketHoursService {

    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);

    private final UnusualWhalesClient unusualWhalesClient;
    private final SpotGexCollectionProperties properties;

    public boolean isRegularMarketOpen() {
        return getRegularMarketStatus().open();
    }

    public MarketStatus getRegularMarketStatus() {
        String proxyTicker = properties.getMarketHours().getProxyTicker();
        try {
            StockStateResponse state = unusualWhalesClient.getStockStateForScheduler(proxyTicker);
            String marketTime = state != null && state.data() != null ? state.data().marketTime() : null;
            if (marketTime != null && !marketTime.isBlank()) {
                boolean isOpen = isRegularSession(marketTime);
                log.debug("[MARKET-HOURS] {} market_time='{}' -> isOpen={}", proxyTicker, marketTime, isOpen);
                return new MarketStatus(isOpen, "uw-stock-state", marketTime, ZonedDateTime.now(resolveFallbackZoneId()));
            }
            log.warn("[MARKET-HOURS] Stock state returned no market_time for {}, using NYSE fallback", proxyTicker);
        } catch (Exception ex) {
            log.warn("[MARKET-HOURS] API check failed for {}, using NYSE fallback: {}", proxyTicker, ex.getMessage());
        }

        ZonedDateTime now = ZonedDateTime.now(resolveFallbackZoneId());
        boolean isOpen = isFallbackMarketOpen(now);
        log.debug("[MARKET-HOURS] Fallback NYSE window check at {} -> isOpen={}", now, isOpen);
        return new MarketStatus(isOpen, "nyse-fallback", isOpen ? "market" : "closed", now);
    }

    boolean isFallbackMarketOpen(ZonedDateTime currentTime) {
        DayOfWeek day = currentTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime now = currentTime.toLocalTime();
        return !now.isBefore(REGULAR_OPEN) && now.isBefore(REGULAR_CLOSE);
    }

    boolean isRegularSession(String marketTime) {
        return switch (marketTime.toLowerCase(Locale.ROOT)) {
            case "market", "regular" -> true;
            default -> false;
        };
    }

    private ZoneId resolveFallbackZoneId() {
        return ZoneId.of(properties.getMarketHours().getFallbackTimezone());
    }

    public record MarketStatus(boolean open, String source, String marketTime, ZonedDateTime checkedAt) {}
}