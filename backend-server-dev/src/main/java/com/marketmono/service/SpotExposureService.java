package com.marketmono.service;

import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for reading request-time spot exposure data from persisted Spot-GEX snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpotExposureService {

    private static final int DEFAULT_STRIKE_LIMIT = 50;
    private static final int DEFAULT_EXPIRY_STRIKE_LIMIT = 500;
    private static final int MAX_EXPIRY_STRIKE_LIMIT = 500;

    private final SpotGexQueryService spotGexQueryService;
    private final StrataTickerFamilyResolver strataTickerFamilyResolver;
    private final CollectionConfigService collectionConfigService;
    private final SpotGexTickerSourcePolicy tickerSourcePolicy;

    /**
     * Get spot exposure aggregated by strike price for a ticker.
     *
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, retained for route compatibility)
     * @param limit Number of results (default 50)
     * @param maxStrike Maximum strike price filter (optional)
     * @return Spot exposure data grouped by strike price
     */
    @Cacheable(value = "spotExposureCache",
               key = "'strike-' + #root.target.spotExposureTickerCacheKey(#ticker) + '-' + #date + '-' + #limit + '-' + #maxStrike",
               cacheManager = "spotExposureCacheManager")
    public List<SpotExposureByStrikeDto> getSpotExposureByStrike(
            String ticker,
            String date,
            Integer limit,
            BigDecimal maxStrike) {
        String normalizedTicker = normalizeTicker(ticker);
        log.debug("Reading persisted spot exposure by strike for {}", normalizedTicker);

        List<SpotGexSnapshotDto> snapshots = latestSnapshotsForFamily(
            normalizedTicker,
            Set.of(),
            null,
            maxStrike
        );
        List<SpotExposureByStrikeDto> rows = aggregateByStrike(normalizedTicker, snapshots);
        return rows.stream()
            .limit(normalizeLimit(limit, DEFAULT_STRIKE_LIMIT, MAX_EXPIRY_STRIKE_LIMIT))
            .toList();
    }

    /**
     * Get spot exposure by expiry and strike for a ticker.
     *
     * @param ticker Stock ticker symbol
     * @param expirations Array of expiration dates
     * @param date Market date (optional, used for DTE filters)
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination
     * @param minStrike Minimum strike price filter
     * @param maxStrike Maximum strike price filter
     * @param minDte Minimum days to expiry filter
     * @param maxDte Maximum days to expiry filter
     * @return Spot exposure data grouped by expiry and strike
     */
    @Cacheable(value = "spotExposureCache",
               key = "'expiry-strike-' + #root.target.spotExposureTickerCacheKey(#ticker) + '-' + (#expirations == null ? 'all' : #expirations.toString()) + '-' + #date + '-' + #limit + '-' + #page + '-' + #minStrike + '-' + #maxStrike + '-' + #minDte + '-' + #maxDte",
               cacheManager = "spotExposureCacheManager")
    public List<SpotExposureByStrikeDto> getSpotExposureByExpiryStrike(
            String ticker,
            List<String> expirations,
            String date,
            Integer limit,
            Integer page,
            Integer minStrike,
            Integer maxStrike,
            Integer minDte,
            Integer maxDte) {
        String normalizedTicker = normalizeTicker(ticker);
        Set<LocalDate> expirationFilter = parseExpirations(expirations);
        BigDecimal minStrikeValue = minStrike == null ? null : BigDecimal.valueOf(minStrike);
        BigDecimal maxStrikeValue = maxStrike == null ? null : BigDecimal.valueOf(maxStrike);
        int finalLimit = normalizeLimit(limit, DEFAULT_EXPIRY_STRIKE_LIMIT, MAX_EXPIRY_STRIKE_LIMIT);
        int offset = normalizePage(page) * finalLimit;

        log.debug("Reading persisted spot exposure by expiry-strike for {} with {} expiration filters",
            normalizedTicker, expirationFilter.size());

        List<SpotGexSnapshotDto> snapshots = latestSnapshotsForFamily(
            normalizedTicker,
            expirationFilter,
            minStrikeValue,
            maxStrikeValue
        );

        return snapshots.stream()
            .filter(row -> expirationFilter.isEmpty() || expirationFilter.contains(row.expiry()))
            .filter(row -> passesDteFilter(row, date, minDte, maxDte))
            .skip(offset)
            .limit(finalLimit)
            .map(SpotExposureService::toSpotExposure)
            .toList();
    }

    public String spotExposureTickerCacheKey(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        return normalizedTicker + '[' + strataTickerFamilyResolver.cacheKey(normalizedTicker) + ']';
    }

    private List<SpotGexSnapshotDto> latestSnapshotsForFamily(
            String displayTicker,
            Set<LocalDate> expirations,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        List<String> roots = spotExposureRootsForRequest(displayTicker, expirations);
        if (roots.isEmpty()) {
            return List.of();
        }

        Map<SpotExposureCellKey, SpotGexSnapshotDto> mergedRows = new LinkedHashMap<>();
        List<LocalDate> requestedExpirations = sortedExpirations(expirations);
        for (String root : roots) {
            if (requestedExpirations.isEmpty()) {
                mergeLatestSnapshots(mergedRows, displayTicker, root, null, minStrike, maxStrike);
            } else {
                for (LocalDate expiry : requestedExpirations) {
                    mergeLatestSnapshots(mergedRows, displayTicker, root, expiry, minStrike, maxStrike);
                }
            }
        }

        return List.copyOf(mergedRows.values());
    }

    private List<String> spotExposureRootsForRequest(String displayTicker, Set<LocalDate> expirations) {
        List<String> roots = strataTickerFamilyResolver.spotExposureRoots(displayTicker);
        if (expirations != null && !expirations.isEmpty()) {
            return roots;
        }
        return configuredSpotExposureRoots(displayTicker, roots);
    }

    private void mergeLatestSnapshots(
            Map<SpotExposureCellKey, SpotGexSnapshotDto> mergedRows,
            String displayTicker,
            String root,
            LocalDate expiry,
            BigDecimal minStrike,
            BigDecimal maxStrike) {
        SpotGexQueryService.LatestSnapshotResult result = spotGexQueryService
            .getLatestSnapshot(root, expiry, minStrike, maxStrike);
        List<SpotGexSnapshotDto> snapshots = result == null ? List.of() : result.data();
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        for (SpotGexSnapshotDto snapshot : snapshots) {
            SpotExposureCellKey key = SpotExposureCellKey.from(snapshot);
            if (key != null) {
                mergedRows.putIfAbsent(key, snapshot.withTicker(displayTicker));
            }
        }
    }

    private static List<LocalDate> sortedExpirations(Set<LocalDate> expirations) {
        if (expirations == null || expirations.isEmpty()) {
            return List.of();
        }
        return expirations.stream()
            .sorted()
            .toList();
    }

    private List<String> configuredSpotExposureRoots(String displayTicker, List<String> roots) {
        List<String> configuredTickers = normalizedConfiguredTickers();
        if (configuredTickers.isEmpty()) {
            return roots;
        }

        if (tickerSourcePolicy.isRestOnlyFamilyConfigured(displayTicker, configuredTickers)) {
            return roots;
        }

        String preferredRoot = roots.getFirst();
        if (!configuredTickers.contains(preferredRoot)) {
            return roots;
        }

        List<String> configuredRoots = roots.stream()
            .filter(configuredTickers::contains)
            .toList();
        return configuredRoots.isEmpty() ? roots : configuredRoots;
    }

    private List<String> normalizedConfiguredTickers() {
        try {
            List<String> configuredTickers = collectionConfigService.getSpotGexTickers();
            if (configuredTickers == null || configuredTickers.isEmpty()) {
                return List.of();
            }
            return configuredTickers.stream()
                .filter(Objects::nonNull)
                .map(ticker -> ticker.trim().toUpperCase(Locale.US))
                .filter(ticker -> !ticker.isBlank())
                .toList();
        } catch (RuntimeException exception) {
            log.debug("Unable to resolve Spot GEX configured tickers for range root filtering: {}", exception.getMessage());
            return List.of();
        }
    }

    private static List<SpotExposureByStrikeDto> aggregateByStrike(String ticker, List<SpotGexSnapshotDto> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }

        Map<BigDecimal, List<SpotGexSnapshotDto>> byStrike = snapshots.stream()
            .filter(row -> row != null && row.strike() != null)
            .collect(Collectors.groupingBy(
                row -> row.strike().stripTrailingZeros(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        return byStrike.values().stream()
            .map(rows -> toSpotExposure(aggregateSnapshot(ticker, rows)))
            .toList();
    }

    private static SpotGexSnapshotDto aggregateSnapshot(String ticker, List<SpotGexSnapshotDto> rows) {
        SpotGexSnapshotDto first = rows.getFirst();
        return new SpotGexSnapshotDto(
            ticker,
            null,
            first.strike(),
            first.snapshotAt(),
            first.price(),
            first.marketDate(),
            first.sourceTime(),
            sum(rows, SpotGexSnapshotDto::callDeltaOi),
            sum(rows, SpotGexSnapshotDto::callGammaOi),
            sum(rows, SpotGexSnapshotDto::callVannaOi),
            sum(rows, SpotGexSnapshotDto::callCharmOi),
            sum(rows, SpotGexSnapshotDto::putDeltaOi),
            sum(rows, SpotGexSnapshotDto::putGammaOi),
            sum(rows, SpotGexSnapshotDto::putVannaOi),
            sum(rows, SpotGexSnapshotDto::putCharmOi),
            sum(rows, SpotGexSnapshotDto::callDeltaVol),
            sum(rows, SpotGexSnapshotDto::callGammaVol),
            sum(rows, SpotGexSnapshotDto::callVannaVol),
            sum(rows, SpotGexSnapshotDto::callCharmVol),
            sum(rows, SpotGexSnapshotDto::putDeltaVol),
            sum(rows, SpotGexSnapshotDto::putGammaVol),
            sum(rows, SpotGexSnapshotDto::putVannaVol),
            sum(rows, SpotGexSnapshotDto::putCharmVol),
            sum(rows, SpotGexSnapshotDto::callDeltaBid),
            sum(rows, SpotGexSnapshotDto::callDeltaAsk),
            sum(rows, SpotGexSnapshotDto::callGammaBid),
            sum(rows, SpotGexSnapshotDto::callGammaAsk),
            sum(rows, SpotGexSnapshotDto::callVannaBid),
            sum(rows, SpotGexSnapshotDto::callVannaAsk),
            sum(rows, SpotGexSnapshotDto::callCharmBid),
            sum(rows, SpotGexSnapshotDto::callCharmAsk),
            sum(rows, SpotGexSnapshotDto::putDeltaBid),
            sum(rows, SpotGexSnapshotDto::putDeltaAsk),
            sum(rows, SpotGexSnapshotDto::putGammaBid),
            sum(rows, SpotGexSnapshotDto::putGammaAsk),
            sum(rows, SpotGexSnapshotDto::putVannaBid),
            sum(rows, SpotGexSnapshotDto::putVannaAsk),
            sum(rows, SpotGexSnapshotDto::putCharmBid),
            sum(rows, SpotGexSnapshotDto::putCharmAsk)
        );
    }

    private static SpotExposureByStrikeDto toSpotExposure(SpotGexSnapshotDto row) {
        return new SpotExposureByStrikeDto(
            row.ticker(),
            row.putDeltaOi(),
            row.callDeltaVol(),
            row.callGammaOi(),
            row.callVannaOi(),
            row.marketDate(),
            row.putVannaVol(),
            row.putCharmVol(),
            row.putGammaVol(),
            row.callVannaVol(),
            row.callVannaBid(),
            row.putVannaBid(),
            row.callGammaVol(),
            row.callCharmBid(),
            row.putDeltaVol(),
            toOffsetDateTime(row.sourceTime()),
            row.putGammaOi(),
            row.putCharmAsk(),
            row.callCharmOi(),
            row.callDeltaOi(),
            row.callDeltaBid(),
            row.callGammaAsk(),
            row.callCharmVol(),
            row.putVannaOi(),
            row.putGammaAsk(),
            row.price(),
            row.putDeltaAsk(),
            row.strike(),
            row.callCharmAsk(),
            row.putDeltaBid(),
            row.callGammaBid(),
            row.putVannaAsk(),
            row.callDeltaAsk(),
            row.callVannaAsk(),
            row.putCharmBid(),
            row.putCharmOi(),
            row.putGammaBid(),
            row.expiry()
        );
    }

    private static boolean passesDteFilter(
        SpotGexSnapshotDto row,
        String date,
        Integer minDte,
        Integer maxDte
    ) {
        if (minDte == null && maxDte == null) {
            return true;
        }
        if (row.expiry() == null) {
            return false;
        }

        LocalDate baseDate = firstNonNull(parseDate(date), row.marketDate(), LocalDate.now());
        long dte = ChronoUnit.DAYS.between(baseDate, row.expiry());
        return (minDte == null || dte >= minDte) && (maxDte == null || dte <= maxDte);
    }

    private static BigDecimal sum(List<SpotGexSnapshotDto> rows, Function<SpotGexSnapshotDto, BigDecimal> extractor) {
        return rows.stream()
            .map(extractor)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Set<LocalDate> parseExpirations(List<String> expirations) {
        if (expirations == null || expirations.isEmpty()) {
            return Set.of();
        }

        return expirations.stream()
            .map(SpotExposureService::parseDate)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return ticker.toUpperCase(Locale.ROOT).trim();
    }

    private static int normalizeLimit(Integer limit, int defaultLimit, int maxLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, maxLimit);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record SpotExposureCellKey(LocalDate expiry, BigDecimal strike) {
        static SpotExposureCellKey from(SpotGexSnapshotDto row) {
            if (row == null || row.expiry() == null || row.strike() == null) {
                return null;
            }
            return new SpotExposureCellKey(row.expiry(), row.strike().stripTrailingZeros());
        }
    }
}
