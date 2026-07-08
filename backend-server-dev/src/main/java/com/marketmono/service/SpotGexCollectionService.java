package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.entity.SpotGexExposure;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.service.spotgex.SpotGexStreamBucketPersistence;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates scheduled spot GEX collection across configured tickers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpotGexCollectionService {

    private final SpotGexCollectionProperties properties;
    private final CollectionConfigService collectionConfigService;
    private final SpotGexFetchService spotGexFetchService;
    private final SpotGexExposureBatchPersistence batchPersistence;
    private final SpotGexRestSnapshotPersistence restSnapshotPersistence;
    private final SpotGexBroadcaster spotGexBroadcaster;
    private final UnusualWhalesClient unusualWhalesClient;
    private final RateLimitService rateLimitService;
    private final SpotGexTickerSourcePolicy tickerSourcePolicy;

    private final Map<String, TickerRuntimeStatus> runtimeStatusByTicker = new ConcurrentHashMap<>();
    private final Object batchRotationLock = new Object();
    private int nextTickerIndex;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public CollectionCycleResult collectAll() {
        Instant collectedAt = Instant.now();
        List<String> tickers = normalizeTickers(collectionConfigService.getSpotGexTickers());
        if (tickers.isEmpty()) {
            log.warn("[SPOT-GEX] No tickers configured for scheduled collection");
            return new CollectionCycleResult(collectedAt, 0, 0, 0, 0, 0, 0, false, List.of());
        }
        if (isDailyProjectionStopTriggered()) {
            tickers.forEach(ticker -> recordSkipped(ticker, tickerTier(ticker), "daily_projection_stop"));
            log.warn("[SPOT-GEX] Collection cycle skipped: projected daily usage is at or above stop threshold {}",
                properties.getDailyProjectionStopThreshold());
            return new CollectionCycleResult(collectedAt, tickers.size(), 0, 0, 0, 0, 0, true, List.of());
        }

        boolean extendedTierPaused = isDailyProjectionWarningTriggered();
        List<TickerCollectionPlan> tickersToCollect = selectTickersForCycle(tickers, collectedAt, extendedTierPaused);
        if (tickersToCollect.isEmpty()) {
            log.info("[SPOT-GEX] Collection cycle skipped: configuredTickers={} dueTickers=0", tickers.size());
            return new CollectionCycleResult(collectedAt, tickers.size(), 0, 0, 0, 0, 0, false, List.of());
        }

        long cycleStartedAt = System.currentTimeMillis();
        int successfulTickers = 0;
        int failedTickers = 0;
        int insertedRows = 0;
        int skippedRows = 0;
        int attemptedRows = 0;
        boolean abortedDueToRateLimit = false;
        List<String> failedTickerSymbols = new ArrayList<>();

        for (int index = 0; index < tickersToCollect.size(); index++) {
            TickerCollectionPlan tickerPlan = tickersToCollect.get(index);
            String ticker = tickerPlan.ticker();
            if (shouldAbortForUwBudget(ticker, tickerPlan.tier())) {
                abortedDueToRateLimit = true;
                log.warn(
                    "[SPOT-GEX] Market data request budget is above target before collecting {}. Aborting remaining cycle.",
                    ticker
                );
                break;
            }

            try {
                TickerCollectionResult tickerResult = collectTicker(ticker, collectedAt, tickerPlan.tier());
                successfulTickers++;
                attemptedRows += tickerResult.attemptedRows();
                insertedRows += tickerResult.insertedRows();
                skippedRows += tickerResult.skippedRows();
            } catch (RateLimitExceededException ex) {
                recordTickerFailure(ticker, ex.getMessage());
                recordCollectionCall(ticker, tickerPlan.tier(), "rate_limit");
                abortedDueToRateLimit = true;
                failedTickers++;
                failedTickerSymbols.add(ticker);
                log.warn("[SPOT-GEX] Rate limit reached while collecting {}. Aborting remaining cycle: {}",
                    ticker, ex.getMessage());
                break;
            } catch (Exception ex) {
                recordTickerFailure(ticker, ex.getMessage());
                recordCollectionCall(ticker, tickerPlan.tier(), "failure");
                failedTickers++;
                failedTickerSymbols.add(ticker);
                log.error("[SPOT-GEX] Failed collection for {}: {}", ticker, ex.getMessage(), ex);
            }

            if (!abortedDueToRateLimit && index < tickersToCollect.size() - 1) {
                pauseBetweenTickers(ticker, properties.getInterTickerDelayMs());
            }
        }

        long elapsedMs = System.currentTimeMillis() - cycleStartedAt;
        log.info("[SPOT-GEX] Collection cycle complete: configuredTickers={}, dueTickers={}, succeeded={}, failed={}, attemptedRows={}, insertedRows={}, skippedRows={}, abortedDueToRateLimit={}, elapsedMs={}, failedTickers={}",
            tickers.size(), tickersToCollect.size(), successfulTickers, failedTickers, attemptedRows, insertedRows, skippedRows,
            abortedDueToRateLimit, elapsedMs, failedTickerSymbols);

        return new CollectionCycleResult(
            collectedAt,
            tickers.size(),
            successfulTickers,
            failedTickers,
            attemptedRows,
            insertedRows,
            skippedRows,
            abortedDueToRateLimit,
            List.copyOf(failedTickerSymbols)
        );
    }

    public CollectionCycleResult collectRestOnlyTickers() {
        Instant collectedAt = Instant.now();
        List<String> configuredTickers = normalizeTickers(collectionConfigService.getSpotGexTickers());
        List<String> configuredRoots = tickerSourcePolicy.collectionRootsForConfiguredTickers(configuredTickers);
        if (configuredTickers.isEmpty() || configuredRoots.isEmpty()) {
            log.debug("[SPOT-GEX] REST-only collection skipped: configuredTickers={} restOnlyRoots={}",
                configuredTickers.size(), configuredRoots.size());
            return new CollectionCycleResult(collectedAt, configuredTickers.size(), 0, 0, 0, 0, 0, false, List.of());
        }
        List<String> rootsToCollect = selectDueRestOnlyRoots(configuredRoots, collectedAt);
        if (rootsToCollect.isEmpty()) {
            log.debug("[SPOT-GEX] REST-only collection skipped: configuredTickers={} restOnlyRoots={} dueRoots=0",
                configuredTickers.size(), configuredRoots.size());
            return new CollectionCycleResult(collectedAt, configuredTickers.size(), 0, 0, 0, 0, 0, false, List.of());
        }
        if (isDailyProjectionStopTriggered()) {
            rootsToCollect.forEach(root -> recordSkipped(root, "rest_only", "daily_projection_stop"));
            log.warn("[SPOT-GEX] REST-only collection skipped: projected daily usage is at or above stop threshold {}",
                properties.getDailyProjectionStopThreshold());
            return new CollectionCycleResult(collectedAt, configuredTickers.size(), 0, 0, 0, 0, 0, true, List.of());
        }

        int successfulTickers = 0;
        int failedTickers = 0;
        int attemptedRows = 0;
        int insertedRows = 0;
        int skippedRows = 0;
        boolean abortedDueToRateLimit = false;
        List<String> failedTickerSymbols = new ArrayList<>();

        for (int index = 0; index < rootsToCollect.size(); index++) {
            String root = rootsToCollect.get(index);
            if (shouldAbortForUwBudget(root, "rest_only")) {
                abortedDueToRateLimit = true;
                break;
            }
            try {
                TickerCollectionResult tickerResult = collectRestOnlyRoot(root, collectedAt);
                successfulTickers++;
                attemptedRows += tickerResult.attemptedRows();
                insertedRows += tickerResult.insertedRows();
                skippedRows += tickerResult.skippedRows();
            } catch (RateLimitExceededException exception) {
                recordTickerFailure(root, exception.getMessage());
                recordCollectionCall(root, "rest_only", "rate_limit");
                failedTickers++;
                failedTickerSymbols.add(root);
                abortedDueToRateLimit = true;
                break;
            } catch (Exception exception) {
                recordTickerFailure(root, exception.getMessage());
                recordCollectionCall(root, "rest_only", "failure");
                failedTickers++;
                failedTickerSymbols.add(root);
                log.error("[SPOT-GEX] Failed REST-only collection for {}: {}", root, exception.getMessage(), exception);
            }

            if (!abortedDueToRateLimit && index < rootsToCollect.size() - 1) {
                pauseBetweenTickers(root, properties.getInterTickerDelayMs());
            }
        }

        log.info("[SPOT-GEX] REST-only collection complete: configuredTickers={}, roots={}, succeeded={}, failed={}, attemptedRows={}, insertedRows={}, skippedRows={}, abortedDueToRateLimit={}, failedRoots={}",
            configuredTickers.size(), rootsToCollect.size(), successfulTickers, failedTickers, attemptedRows, insertedRows,
            skippedRows, abortedDueToRateLimit, failedTickerSymbols);

        return new CollectionCycleResult(
            collectedAt,
            configuredTickers.size(),
            successfulTickers,
            failedTickers,
            attemptedRows,
            insertedRows,
            skippedRows,
            abortedDueToRateLimit,
            List.copyOf(failedTickerSymbols)
        );
    }

    private List<String> selectDueRestOnlyRoots(List<String> roots, Instant now) {
        List<String> dueRoots = new ArrayList<>();
        for (String root : roots) {
            if (isRestOnlyRootDue(root, now)) {
                dueRoots.add(root);
            } else {
                recordSkipped(root, "rest_only", "cooldown");
            }
        }
        return dueRoots;
    }

    private boolean isRestOnlyRootDue(String root, Instant now) {
        TickerRuntimeStatus status = runtimeStatusByTicker.get(normalizeTicker(root));
        if (status == null || status.lastAttemptAt() == null) {
            return true;
        }

        long intervalMs = properties.getRestOnlyIntervalMs();
        return intervalMs <= 0 || !status.lastAttemptAt().plusMillis(intervalMs).isAfter(now);
    }

    private boolean shouldAbortForUwBudget(String ticker, String tier) {
        if (isDailyProjectionStopTriggered()) {
            recordTickerFailure(ticker, "Projected daily usage above stop threshold");
            recordSkipped(ticker, tier, "daily_projection_stop");
            return true;
        }

        if (!properties.isAbortWhenUwBudgetAboveTarget()) {
            return false;
        }

        boolean aboveTarget = unusualWhalesClient.isRequestBudgetAboveTarget();
        if (aboveTarget) {
            recordTickerFailure(ticker, "Market data request budget above target");
            recordSkipped(ticker, tier, "budget_target");
        }
        return aboveTarget;
    }

    TickerCollectionResult collectTicker(String ticker, Instant collectedAt) {
        return collectTicker(ticker, collectedAt, "core");
    }

    TickerCollectionResult collectTicker(String ticker, Instant collectedAt, String tier) {
        long startedAt = System.currentTimeMillis();
        List<LocalDate> expirations = spotGexFetchService.getExpirationDates(ticker, null);
        if (expirations.isEmpty()) {
            recordTickerSuccess(ticker, collectedAt, 0, 0, 0);
            recordCollectionCall(ticker, tier, "success_empty");
            log.warn("[SPOT-GEX] No expirations found for {} — skipping persistence", ticker);
            return new TickerCollectionResult(ticker, 0, 0, 0, 0);
        }

        List<SpotExposureByStrikeDto> rows = spotGexFetchService.fetchAllSpotExposurePages(
            ticker,
            expirations,
            null,
            properties.getPageSize(),
            properties.getInterPageDelayMs()
        );

        if (rows.isEmpty()) {
            recordTickerSuccess(ticker, collectedAt, 0, 0, 0);
            recordCollectionCall(ticker, tier, "success_empty");
            log.warn("[SPOT-GEX] No expiry-strike rows returned for {} after fetching {} expirations", ticker, expirations.size());
            return new TickerCollectionResult(ticker, expirations.size(), 0, 0, 0);
        }

        List<SpotGexExposure> exposures = rows.stream()
            .map(row -> toExposure(ticker, row, collectedAt))
            .toList();

        SpotGexExposureBatchPersistence.BatchPersistResult persistResult = batchPersistence.persistBatch(exposures);
        recordTickerSuccess(
            ticker,
            collectedAt,
            persistResult.attempted(),
            persistResult.inserted(),
            persistResult.skipped()
        );
        spotGexBroadcaster.broadcastTickerSnapshot(ticker, collectedAt, exposures);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        recordCollectionCall(ticker, tier, "success");

        log.info("[SPOT-GEX] Collected {} rows for {} across {} expirations in {} ms (inserted={}, skipped={})",
            persistResult.attempted(), ticker, expirations.size(), elapsedMs,
            persistResult.inserted(), persistResult.skipped());

        return new TickerCollectionResult(
            ticker,
            expirations.size(),
            persistResult.attempted(),
            persistResult.inserted(),
            persistResult.skipped()
        );
    }

    private TickerCollectionResult collectRestOnlyRoot(String root, Instant collectedAt) {
        long startedAtNanos = System.nanoTime();
        List<LocalDate> expirations = spotGexFetchService.getExpirationDates(root, null);
        if (expirations.isEmpty()) {
            recordTickerSuccess(root, collectedAt, 0, 0, 0);
            recordCollectionCall(root, "rest_only", "success_empty");
            recordRestOnlyCollectionDetails(root, "success_empty", 0, 0, 0, 0, startedAtNanos);
            log.warn("[SPOT-GEX] No expirations found for REST-only root {} — skipping read-model persistence", root);
            return new TickerCollectionResult(root, 0, 0, 0, 0);
        }

        int pageSize = properties.getPageSize();
        List<SpotExposureByStrikeDto> rows = spotGexFetchService.fetchAllSpotExposurePages(
            root,
            expirations,
            null,
            pageSize,
            properties.getInterPageDelayMs()
        );

        if (rows.isEmpty()) {
            recordTickerSuccess(root, collectedAt, 0, 0, 0);
            recordCollectionCall(root, "rest_only", "success_empty");
            recordRestOnlyCollectionDetails(
                root,
                "success_empty",
                estimateFetchedPages(rows.size(), pageSize),
                0,
                0,
                0,
                startedAtNanos
            );
            log.warn("[SPOT-GEX] No expiry-strike rows returned for REST-only root {} after fetching {} expirations",
                root, expirations.size());
            return new TickerCollectionResult(root, expirations.size(), 0, 0, 0);
        }

        SpotGexStreamBucketPersistence.BatchPersistResult persistResult =
            restSnapshotPersistence.persistSnapshot(root, rows, collectedAt);
        recordTickerSuccess(
            root,
            collectedAt,
            persistResult.attempted(),
            persistResult.upserted(),
            persistResult.skippedOlderReplay()
        );
        recordCollectionCall(root, "rest_only", "success");
        recordRestOnlyCollectionDetails(
            root,
            "success",
            estimateFetchedPages(rows.size(), pageSize),
            persistResult.attempted(),
            persistResult.upserted(),
            persistResult.skippedOlderReplay(),
            startedAtNanos
        );
        log.info("[SPOT-GEX] Persisted {} REST-only read-model rows for {} across {} expirations (upserted={}, skipped={})",
            persistResult.attempted(), root, expirations.size(), persistResult.upserted(), persistResult.skippedOlderReplay());

        return new TickerCollectionResult(
            root,
            expirations.size(),
            persistResult.attempted(),
            persistResult.upserted(),
            persistResult.skippedOlderReplay()
        );
    }

    private List<TickerCollectionPlan> selectTickersForCycle(List<String> tickers, Instant now, boolean extendedTierPaused) {
        List<TickerCollectionPlan> plans = tickerPlans(tickers);
        if (plans.isEmpty()) {
            return List.of();
        }

        int maxTickersPerCycle = properties.getMaxTickersPerCycle();
        int maxToCollect = maxTickersPerCycle > 0 ? Math.min(maxTickersPerCycle, plans.size()) : plans.size();
        List<TickerCollectionPlan> selected = new ArrayList<>();

        synchronized (batchRotationLock) {
            int startIndex = Math.floorMod(nextTickerIndex, plans.size());
            int nextIndex = startIndex;
            for (int offset = 0; offset < plans.size() && selected.size() < maxToCollect; offset++) {
                int currentIndex = (startIndex + offset) % plans.size();
                TickerCollectionPlan plan = plans.get(currentIndex);
                if (extendedTierPaused && !plan.core()) {
                    recordSkipped(plan.ticker(), plan.tier(), "daily_projection_warning");
                    continue;
                }
                if (isTickerDue(plan, now)) {
                    selected.add(plan);
                    nextIndex = (currentIndex + 1) % plans.size();
                } else {
                    recordSkipped(plan.ticker(), plan.tier(), "cooldown");
                }
            }
            nextTickerIndex = nextIndex;
        }

        return selected;
    }

    private boolean isDailyProjectionStopTriggered() {
        return isDailyProjectionAtOrAbove(properties.getDailyProjectionStopThreshold());
    }

    private boolean isDailyProjectionWarningTriggered() {
        return isDailyProjectionAtOrAbove(properties.getDailyProjectionWarningThreshold());
    }

    private boolean isDailyProjectionAtOrAbove(int threshold) {
        return threshold > 0 && rateLimitService.isDailyProjectionAtOrAbove(threshold);
    }

    private String tickerTier(String ticker) {
        Set<String> coreTickers = normalizedSet(properties.getCoreTickers());
        return coreTickers.isEmpty() || coreTickers.contains(normalizeTicker(ticker)) ? "core" : "extended";
    }

    private List<TickerCollectionPlan> tickerPlans(List<String> tickers) {
        Set<String> coreTickers = normalizedSet(properties.getCoreTickers());
        return tickers.stream()
            .map(ticker -> {
                boolean core = coreTickers.isEmpty() || coreTickers.contains(ticker);
                return new TickerCollectionPlan(ticker, core ? "core" : "extended", core);
            })
            .toList();
    }

    private boolean isTickerDue(TickerCollectionPlan tickerPlan, Instant now) {
        TickerRuntimeStatus status = runtimeStatusByTicker.get(tickerPlan.ticker());
        if (status == null || status.lastAttemptAt() == null) {
            return true;
        }

        long intervalMs = tickerPlan.core()
            ? properties.getCoreTickerIntervalMs()
            : properties.getExtendedTickerIntervalMs();
        return intervalMs <= 0 || !status.lastAttemptAt().plusMillis(intervalMs).isAfter(now);
    }

    private List<String> normalizeTickers(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        return tickers.stream()
            .map(this::normalizeTicker)
            .filter(ticker -> !ticker.isBlank())
            .distinct()
            .toList();
    }

    private Set<String> normalizedSet(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new HashSet<>();
        for (String ticker : tickers) {
            String value = normalizeTicker(ticker);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? "" : ticker.toUpperCase(Locale.ROOT).trim();
    }

    private SpotGexExposure toExposure(String ticker, SpotExposureByStrikeDto row, Instant collectedAt) {
        return SpotGexExposure.builder()
            .ticker(ticker.toUpperCase())
            .expiry(row.expiry())
            .strike(row.strike())
            .collectedAt(collectedAt)
            .price(row.price())
            .marketDate(row.date())
            .sourceTime(row.time() != null ? row.time().toInstant() : null)
            .callDeltaOi(row.callDeltaOi())
            .callGammaOi(row.callGammaOi())
            .callVannaOi(row.callVannaOi())
            .callCharmOi(row.callCharmOi())
            .putDeltaOi(row.putDeltaOi())
            .putGammaOi(row.putGammaOi())
            .putVannaOi(row.putVannaOi())
            .putCharmOi(row.putCharmOi())
            .callDeltaVol(row.callDeltaVol())
            .callGammaVol(row.callGammaVol())
            .callVannaVol(row.callVannaVol())
            .callCharmVol(row.callCharmVol())
            .putDeltaVol(row.putDeltaVol())
            .putGammaVol(row.putGammaVol())
            .putVannaVol(row.putVannaVol())
            .putCharmVol(row.putCharmVol())
            .callDeltaBid(row.callDeltaBid())
            .callDeltaAsk(row.callDeltaAsk())
            .callGammaBid(row.callGammaBid())
            .callGammaAsk(row.callGammaAsk())
            .callVannaBid(row.callVannaBid())
            .callVannaAsk(row.callVannaAsk())
            .callCharmBid(row.callCharmBid())
            .callCharmAsk(row.callCharmAsk())
            .putDeltaBid(row.putDeltaBid())
            .putDeltaAsk(row.putDeltaAsk())
            .putGammaBid(row.putGammaBid())
            .putGammaAsk(row.putGammaAsk())
            .putVannaBid(row.putVannaBid())
            .putVannaAsk(row.putVannaAsk())
            .putCharmBid(row.putCharmBid())
            .putCharmAsk(row.putCharmAsk())
            .build();
    }

    private void pauseBetweenTickers(String ticker, long delayMs) {
        if (delayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pacing spot GEX collection after " + ticker, ex);
        }
    }

    public Map<String, TickerRuntimeStatus> getRuntimeStatusSnapshot() {
        return Map.copyOf(runtimeStatusByTicker);
    }

    private void recordTickerSuccess(
            String ticker,
            Instant collectedAt,
            int attemptedRows,
            int insertedRows,
            int skippedRows) {
        String normalizedTicker = ticker.toUpperCase();
        TickerRuntimeStatus previous = runtimeStatusByTicker.get(normalizedTicker);
        runtimeStatusByTicker.put(normalizedTicker, new TickerRuntimeStatus(
            Instant.now(),
            collectedAt,
            null,
            previous != null ? previous.errorCount() : 0,
            attemptedRows,
            insertedRows,
            skippedRows,
            true
        ));
    }

    private void recordTickerFailure(String ticker, String errorMessage) {
        String normalizedTicker = ticker.toUpperCase();
        TickerRuntimeStatus previous = runtimeStatusByTicker.get(normalizedTicker);
        runtimeStatusByTicker.put(normalizedTicker, new TickerRuntimeStatus(
            Instant.now(),
            previous != null ? previous.lastSuccessfulCollectionAt() : null,
            errorMessage,
            previous != null ? previous.errorCount() + 1 : 1,
            previous != null ? previous.lastAttemptedRows() : 0,
            previous != null ? previous.lastInsertedRows() : 0,
            previous != null ? previous.lastSkippedRows() : 0,
            false
        ));
    }

    private void recordCollectionCall(String ticker, String tier, String result) {
        if (meterRegistry == null) {
            return;
        }

        Counter.builder("marketmono.spot.gex.collection.calls")
            .description("Spot-GEX collection ticker attempts by configured ticker, tier, and result")
            .tag("ticker", metricTicker(ticker))
            .tag("tier", metricTier(tier))
            .tag("result", result != null ? result : "unknown")
            .register(meterRegistry)
            .increment();
    }

    private void recordSkipped(String ticker, String tier, String reason) {
        if (meterRegistry == null) {
            return;
        }

        Counter.builder("marketmono.spot.gex.collection.skipped")
            .description("Spot-GEX collection ticker skips by configured ticker, tier, and reason")
            .tag("ticker", metricTicker(ticker))
            .tag("tier", metricTier(tier))
            .tag("reason", reason != null ? reason : "unknown")
            .register(meterRegistry)
            .increment();
    }

    private void recordRestOnlyCollectionDetails(
            String ticker,
            String result,
            int fetchedPages,
            int attemptedRows,
            int insertedRows,
            int skippedRows,
            long startedAtNanos) {
        if (meterRegistry == null) {
            return;
        }

        String normalizedTicker = metricTicker(ticker);
        String normalizedResult = result != null ? result : "unknown";
        Timer.builder("marketmono.spot.gex.collection.duration")
            .description("Spot-GEX collection duration by configured ticker, tier, and result")
            .tag("ticker", normalizedTicker)
            .tag("tier", "rest_only")
            .tag("result", normalizedResult)
            .register(meterRegistry)
            .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        DistributionSummary.builder("marketmono.spot.gex.collection.fetched.pages")
            .description("Spot-GEX collection fetched pages by configured ticker, tier, and result")
            .tag("ticker", normalizedTicker)
            .tag("tier", "rest_only")
            .tag("result", normalizedResult)
            .register(meterRegistry)
            .record(fetchedPages);
        recordCollectionRows(normalizedTicker, normalizedResult, "attempted", attemptedRows);
        recordCollectionRows(normalizedTicker, normalizedResult, "inserted", insertedRows);
        recordCollectionRows(normalizedTicker, normalizedResult, "skipped", skippedRows);
    }

    private void recordCollectionRows(String ticker, String result, String rowType, int rows) {
        DistributionSummary.builder("marketmono.spot.gex.collection.rows")
            .description("Spot-GEX collection rows by configured ticker, tier, result, and row type")
            .tag("ticker", ticker)
            .tag("tier", "rest_only")
            .tag("result", result)
            .tag("row_type", rowType)
            .register(meterRegistry)
            .record(rows);
    }

    private int estimateFetchedPages(int rowCount, int pageSize) {
        if (rowCount <= 0) {
            return 0;
        }
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) rowCount / pageSize);
    }

    private String metricTicker(String ticker) {
        String normalized = normalizeTicker(ticker);
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String metricTier(String tier) {
        return tier == null || tier.isBlank() ? "unknown" : tier;
    }

    public record CollectionCycleResult(
        Instant collectedAt,
        int configuredTickers,
        int successfulTickers,
        int failedTickers,
        int attemptedRows,
        int insertedRows,
        int skippedRows,
        boolean abortedDueToRateLimit,
        List<String> failedTickersList
    ) {}

    record TickerCollectionResult(
        String ticker,
        int expirationCount,
        int attemptedRows,
        int insertedRows,
        int skippedRows
    ) {}

    private record TickerCollectionPlan(String ticker, String tier, boolean core) {}

    public record TickerRuntimeStatus(
        Instant lastAttemptAt,
        Instant lastSuccessfulCollectionAt,
        String lastError,
        int errorCount,
        int lastAttemptedRows,
        int lastInsertedRows,
        int lastSkippedRows,
        boolean lastCycleSuccessful
    ) {}
}
