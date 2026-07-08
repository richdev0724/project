package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import com.marketmono.dto.OptionChainsResponseDto;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.dto.SpotExposureResponseDto;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.util.OptionChainParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper service for uncached spot GEX collection fetches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpotGexFetchService {

    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final Set<String> EXPIRY_BREAKDOWN_TICKERS = Set.of("SPX", "SPXW", "VIX", "VIXW");
    private static final String OPTION_CHAINS_OP = "option chains";
    private static final String EXPIRY_BREAKDOWN_OP = "expiry breakdown";
    private static final String SPOT_EXPOSURE_BY_EXPIRY_STRIKE_OP = "spot exposure by expiry strike";
    private static final Duration EXPIRATION_CACHE_TTL = Duration.ofMinutes(30);
    private static final String EXPIRATION_CACHE_REQUESTS_METRIC =
        "marketmono.spot.gex.expiration.cache.requests";
    private static final String REST_REQUESTS_METRIC = "marketmono.spot.gex.rest.requests";
    private static final String OPTION_CHAINS_REST_OPERATION = "option_chains";
    private static final String EXPIRY_BREAKDOWN_REST_OPERATION = "expiry_breakdown";
    private static final String SPOT_EXPOSURE_REST_OPERATION = "spot_exposure_by_expiry_strike";

    private final UnusualWhalesClient unusualWhalesClient;
    private final RateLimitService rateLimitService;
    private final StrataTickerFamilyResolver strataTickerFamilyResolver;
    private final Map<ExpirationCacheKey, CachedExpirations> expirationCache = new ConcurrentHashMap<>();
    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    @Autowired(required = false)
    private SpotGexCollectionProperties spotGexCollectionProperties;

    public List<LocalDate> getExpirationDates(String ticker, String date) {
        if (!StringUtils.hasText(ticker)) {
            return List.of();
        }

        String normalizedTicker = ticker.toUpperCase(Locale.ROOT).trim();
        ExpirationCacheKey cacheKey = new ExpirationCacheKey(normalizedTicker, normalizeDate(date));
        CachedExpirations cached = expirationCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            recordExpirationCacheRequest("hit");
            return cached.expirations();
        }

        if (shouldUseExpiryBreakdown(normalizedTicker, date)) {
            return getExpirationDatesFromExpiryBreakdown(normalizedTicker, cacheKey, now);
        }

        boolean restAttempted = false;
        boolean restRecorded = false;
        try {
            checkRateLimitOrRecordRejection(OPTION_CHAINS_OP);
            restAttempted = true;
            OptionChainsResponseDto response = unusualWhalesClient.getOptionChainsUncached(normalizedTicker, date);
            if (response == null) {
                recordRestRequest(OPTION_CHAINS_REST_OPERATION, "error");
                restRecorded = true;
                throw new IllegalStateException("Market data provider returned no option chains response for " + ticker);
            }
            recordRestRequest(OPTION_CHAINS_REST_OPERATION, "success");
            restRecorded = true;
            if (response.data() == null || response.data().isEmpty()) {
                log.warn("[SPOT-GEX] No option chains returned for {}", ticker);
                expirationCache.put(cacheKey, new CachedExpirations(List.of(), now.plus(EXPIRATION_CACHE_TTL)));
                recordExpirationCacheRequest("miss");
                return List.of();
            }

            List<LocalDate> expirations = OptionChainParser.extractUniqueExpirations(response.data())
                .stream()
                .toList();
            expirationCache.put(cacheKey, new CachedExpirations(expirations, now.plus(EXPIRATION_CACHE_TTL)));
            recordExpirationCacheRequest("miss");

            log.info("[SPOT-GEX] Extracted {} expirations from {} option symbols for {}",
                expirations.size(), response.data().size(), ticker);
            return expirations;
        } catch (RuntimeException exception) {
            if (restAttempted && !restRecorded) {
                recordRestRequest(OPTION_CHAINS_REST_OPERATION, "error");
            }
            recordExpirationCacheRequest("error");
            throw exception;
        }
    }

    private List<LocalDate> getExpirationDatesFromExpiryBreakdown(
            String normalizedTicker,
            ExpirationCacheKey cacheKey,
            Instant now) {
        boolean restAttempted = false;
        boolean restRecorded = false;
        try {
            List<String> expiryBreakdownRoots = strataTickerFamilyResolver.expiryBreakdownRoots(normalizedTicker);
            List<LocalDate> rawExpirations = new ArrayList<>();
            int rowCount = 0;
            for (String expiryBreakdownRoot : expiryBreakdownRoots) {
                checkRateLimitOrRecordRejection(EXPIRY_BREAKDOWN_OP);
                restAttempted = true;
                restRecorded = false;
                ExpiryBreakdownResponseDto response = unusualWhalesClient.getExpiryBreakdownUncached(expiryBreakdownRoot);
                if (response == null) {
                    recordRestRequest(EXPIRY_BREAKDOWN_REST_OPERATION, "error");
                    restRecorded = true;
                    throw new IllegalStateException(
                        "Market data provider returned no expiry breakdown response for " + expiryBreakdownRoot
                    );
                }
                recordRestRequest(EXPIRY_BREAKDOWN_REST_OPERATION, "success");
                restRecorded = true;
                if (response.data() == null || response.data().isEmpty()) {
                    log.warn("[SPOT-GEX] No expiry breakdown rows returned for {}", expiryBreakdownRoot);
                    continue;
                }

                rowCount += response.data().size();
                rawExpirations.addAll(response.data().stream()
                    .filter(row -> row != null && row.expires() != null)
                    .map(row -> row.expires())
                    .toList());
            }

            if (rawExpirations.isEmpty()) {
                expirationCache.put(cacheKey, new CachedExpirations(List.of(), now.plus(EXPIRATION_CACHE_TTL)));
                recordExpirationCacheRequest("miss");
                return List.of();
            }

            List<LocalDate> expirations = rawExpirations.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
            expirationCache.put(cacheKey, new CachedExpirations(expirations, now.plus(EXPIRATION_CACHE_TTL)));
            recordExpirationCacheRequest("miss");

            log.info("[SPOT-GEX] Extracted {} expirations from {} expiry breakdown rows for {}",
                expirations.size(), rowCount, normalizedTicker);
            return expirations;
        } catch (RuntimeException exception) {
            if (restAttempted && !restRecorded) {
                recordRestRequest(EXPIRY_BREAKDOWN_REST_OPERATION, "error");
            }
            recordExpirationCacheRequest("error");
            throw exception;
        }
    }

    private static boolean shouldUseExpiryBreakdown(String normalizedTicker, String date) {
        return !StringUtils.hasText(date) && EXPIRY_BREAKDOWN_TICKERS.contains(normalizedTicker);
    }

    private void recordExpirationCacheRequest(String result) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(EXPIRATION_CACHE_REQUESTS_METRIC)
            .tag("result", result)
            .register(meterRegistry)
            .increment();
    }

    private void recordRestRequest(String operation, String result) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(REST_REQUESTS_METRIC)
            .tag("operation", operation)
            .tag("result", result)
            .tag("source_mode", spotGexSourceMode())
            .register(meterRegistry)
            .increment();
    }

    private String spotGexSourceMode() {
        if (spotGexCollectionProperties == null || spotGexCollectionProperties.getSource() == null) {
            return "unknown";
        }
        return spotGexCollectionProperties.getSource().name().toLowerCase(Locale.ROOT);
    }

    @Cacheable(
        value = "spotExposureCache",
        key = "'spot-gex-on-demand-' + #ticker + '-' + (#expirations == null ? 'none' : #expirations.toString())",
        cacheManager = "spotExposureCacheManager",
        sync = true
    )
    public List<SpotExposureByStrikeDto> fetchAllSpotExposurePages(String ticker, List<LocalDate> expirations) {
        return fetchAllSpotExposurePages(ticker, expirations, null, DEFAULT_PAGE_SIZE);
    }

    public List<SpotExposureByStrikeDto> fetchAllSpotExposurePages(
            String ticker,
            List<LocalDate> expirations,
            String date,
            int limit,
            long interPageDelayMs) {
        return fetchAllSpotExposurePagesInternal(ticker, expirations, date, limit, interPageDelayMs);
    }

    public List<SpotExposureByStrikeDto> fetchAllSpotExposurePages(
            String ticker,
            List<LocalDate> expirations,
            String date,
            int limit) {
        return fetchAllSpotExposurePagesInternal(ticker, expirations, date, limit, 0);
    }

    private List<SpotExposureByStrikeDto> fetchAllSpotExposurePagesInternal(
            String ticker,
            List<LocalDate> expirations,
            String date,
            int limit,
            long interPageDelayMs) {
        if (!StringUtils.hasText(ticker) || expirations == null || expirations.isEmpty()) {
            return List.of();
        }

        List<String> formattedExpirations = expirations.stream()
            .map(LocalDate::toString)
            .toList();

        List<SpotExposureByStrikeDto> allRows = new ArrayList<>();
        int currentPage = 0;
        int fetchedPages = 0;

        while (true) {
            checkRateLimitOrRecordRejection(SPOT_EXPOSURE_BY_EXPIRY_STRIKE_OP);
            SpotExposureResponseDto response;
            try {
                response = unusualWhalesClient.getSpotExposureByExpiryStrikeUncached(
                    ticker.toUpperCase(),
                    formattedExpirations,
                    date,
                    limit,
                    currentPage,
                    null,
                    null,
                    null,
                    null
                );
            } catch (RuntimeException exception) {
                recordRestRequest(SPOT_EXPOSURE_REST_OPERATION, "error");
                throw exception;
            }

            if (response == null) {
                recordRestRequest(SPOT_EXPOSURE_REST_OPERATION, "error");
                throw new IllegalStateException(
                    "Market data provider returned no expiry-strike response for " + ticker + " on page " + currentPage
                );
            }
            recordRestRequest(SPOT_EXPOSURE_REST_OPERATION, "success");

            List<SpotExposureByStrikeDto> currentPageRows = response.data();
            if (currentPageRows == null || currentPageRows.isEmpty()) {
                break;
            }

            fetchedPages++;
            allRows.addAll(currentPageRows);
            log.debug("[SPOT-GEX] Fetched {} rows for {} on page {}",
                currentPageRows.size(), ticker, currentPage);

            if (currentPageRows.size() < limit) {
                break;
            }

            pauseBetweenPages(ticker, currentPage, interPageDelayMs);
            currentPage++;
        }

        log.info("[SPOT-GEX] Fetched {} total expiry-strike rows for {} across {} pages",
            allRows.size(), ticker, fetchedPages);
        return allRows;
    }

    private void checkRateLimitOrRecordRejection(String operationName) {
        try {
            rateLimitService.checkRateLimit();
        } catch (RateLimitExceededException ex) {
            unusualWhalesClient.recordRateLimitPreflightRejection(operationName);
            throw ex;
        }
    }

    private String normalizeDate(String date) {
        return StringUtils.hasText(date) ? date.trim() : "latest";
    }

    private void pauseBetweenPages(String ticker, int currentPage, long interPageDelayMs) {
        if (interPageDelayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(interPageDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                String.format("Interrupted while pacing spot GEX page fetch for %s after page %d", ticker, currentPage),
                ex
            );
        } catch (RateLimitExceededException ex) {
            throw ex;
        }
    }

    private record ExpirationCacheKey(String ticker, String date) {}

    private record CachedExpirations(List<LocalDate> expirations, Instant expiresAt) {}
}
