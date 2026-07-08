package com.marketmono.controller;

import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.service.SpotGexQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for persisted spot GEX snapshots and collection status.
 */
@RestController
@RequestMapping("/api/v1/spot-gex")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SpotGexController {

    private static final String HISTORY_TIMEOUT_MESSAGE =
        "Spot GEX history query timed out. Retry shortly or use a narrower range.";
    private static final String REPLAY_TIMEOUT_MESSAGE =
        "Spot GEX replay query timed out. Retry shortly or choose another minute.";
    private static final String METRIC_HISTORY_DURATION = "spot_gex.history.query.duration";
    private static final String METRIC_HISTORY_ROWS = "spot_gex.history.rows";
    private static final String METRIC_HISTORY_TIMEOUTS = "spot_gex.history.timeouts";

    private final SpotGexQueryService spotGexQueryService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @GetMapping("/{ticker}/latest")
    public ResponseEntity<Map<String, Object>> getLatestSnapshot(
            @PathVariable String ticker,
            @RequestParam(required = false) LocalDate expiry,
            @RequestParam(name = "min_strike", required = false) BigDecimal minStrike,
            @RequestParam(name = "max_strike", required = false) BigDecimal maxStrike) {
        log.info("GET /api/v1/spot-gex/{}/latest - expiry: {}, minStrike: {}, maxStrike: {}",
            ticker, expiry, minStrike, maxStrike);

        SpotGexQueryService.LatestSnapshotResult result =
            spotGexQueryService.getLatestSnapshot(ticker, expiry, minStrike, maxStrike);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("collectedAt", result.collectedAt());
        response.put("source", result.source());
        response.put("coverageMode", result.coverageMode());
        response.put("rowCount", result.rowCount());
        response.put("availableExpiryCount", result.availableExpiryCount());
        response.put("availableStrikeCount", result.availableStrikeCount());
        response.put("data", result.data());
        putIfPresent(response, "fallbackReason", result.fallbackReason());
        putIfPresent(response, "latestBucketStart", result.latestBucketStart());
        putIfPresent(response, "latestBucketLagMillis", result.latestBucketLagMillis());
        putIfPresent(response, "streamConnected", result.streamConnected());
        putIfPresent(response, "streamSubscribed", result.streamSubscribed());
        putIfPresent(response, "streamStale", result.streamStale());
        if (result.degraded()) {
            response.put("degraded", true);
            response.put("code", result.code());
            response.put("message", result.message());
        }
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<Map<String, Object>> getHistory(
            String ticker,
            Instant since,
            String bucket,
            LocalDate expiry,
            BigDecimal strike) {
        return getHistory(ticker, since, bucket, expiry, strike, null, null);
    }

    @GetMapping("/{ticker}/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable String ticker,
            @RequestParam Instant since,
            @RequestParam(defaultValue = "5m") String bucket,
            @RequestParam(required = false) LocalDate expiry,
            @RequestParam(required = false) BigDecimal strike,
            @RequestParam(name = "min_strike", required = false) BigDecimal minStrike,
            @RequestParam(name = "max_strike", required = false) BigDecimal maxStrike) {
        log.info("GET /api/v1/spot-gex/{}/history - since: {}, bucket: {}, expiry: {}, strike: {}, minStrike: {}, maxStrike: {}",
            ticker, since, bucket, expiry, strike, minStrike, maxStrike);

        long startedAtNanos = System.nanoTime();
        SpotGexQueryService.HistoryResult historyResult =
            spotGexQueryService.getHistoryResult(ticker, since, bucket, expiry, strike, minStrike, maxStrike);
        List<?> history = historyResult.data();
        recordHistorySuccessMetric(ticker, bucket, history.size(), startedAtNanos);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("bucket", bucket);
        response.put("data", history);
        if (historyResult.degraded()) {
            response.put("degraded", true);
            response.put("code", historyResult.degradationCode());
            response.put("message", historyResult.degradationMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticker}/replay-session")
    public ResponseEntity<Map<String, Object>> getReplaySession(
            @PathVariable String ticker,
            @RequestParam(required = false) LocalDate date) {
        log.info("GET /api/v1/spot-gex/{}/replay-session - date: {}", ticker, date);

        try {
            SpotGexQueryService.ReplaySessionResult result =
                spotGexQueryService.getReplaySession(ticker, date);
            return ResponseEntity.ok(replaySessionResponse(result));
        } catch (IllegalArgumentException exception) {
            return invalidReplayRequest(exception);
        } catch (QueryTimeoutException | TransactionTimedOutException | DataAccessResourceFailureException exception) {
            return replayTimeout(exception);
        }
    }

    @GetMapping("/{ticker}/replay-snapshot")
    public ResponseEntity<Map<String, Object>> getReplaySnapshot(
            @PathVariable String ticker,
            @RequestParam Instant at,
            @RequestParam(required = false) LocalDate expiry,
            @RequestParam(name = "min_strike", required = false) BigDecimal minStrike,
            @RequestParam(name = "max_strike", required = false) BigDecimal maxStrike) {
        log.info(
            "GET /api/v1/spot-gex/{}/replay-snapshot - at: {}, expiry: {}, minStrike: {}, maxStrike: {}",
            ticker,
            at,
            expiry,
            minStrike,
            maxStrike
        );

        try {
            SpotGexQueryService.ReplaySnapshotResult result =
                spotGexQueryService.getReplaySnapshot(ticker, at, expiry, minStrike, maxStrike);
            HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.NOT_FOUND;
            return ResponseEntity.status(status).body(replaySnapshotResponse(result));
        } catch (IllegalArgumentException exception) {
            return invalidReplayRequest(exception);
        } catch (QueryTimeoutException | TransactionTimedOutException | DataAccessResourceFailureException exception) {
            return replayTimeout(exception);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus() {
        log.info("GET /api/v1/spot-gex/status");

        List<SpotGexCollectionStatusDto> status = spotGexQueryService.getCollectionStatus();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", status);
        response.put("stream", spotGexQueryService.getStreamStatus());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler({
        QueryTimeoutException.class,
        TransactionTimedOutException.class,
        DataAccessResourceFailureException.class
    })
    public ResponseEntity<Map<String, Object>> handleSpotGexHistoryTimeout(RuntimeException exception) {
        log.warn("[SPOT-GEX] History query timed out: {}", exception.getMessage(), exception);
        recordHistoryTimeoutMetric();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "success", false,
            "code", "SPOT_GEX_HISTORY_TIMEOUT",
            "message", HISTORY_TIMEOUT_MESSAGE
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSpotGexHistoryRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "success", false,
            "code", "INVALID_SPOT_GEX_HISTORY_REQUEST",
            "message", exception.getMessage()
        ));
    }

    private void recordHistorySuccessMetric(String ticker, String bucket, int rows, long startedAtNanos) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        String normalizedTicker = normalizeMetricValue(ticker);
        String normalizedBucket = normalizeMetricValue(bucket == null ? "5m" : bucket);
        Timer.builder(METRIC_HISTORY_DURATION)
            .tag("ticker", normalizedTicker)
            .tag("bucket", normalizedBucket)
            .tag("outcome", "success")
            .register(meterRegistry)
            .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);

        meterRegistry.summary(METRIC_HISTORY_ROWS, "ticker", normalizedTicker, "bucket", normalizedBucket)
            .record(rows);
    }

    private void recordHistoryTimeoutMetric() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }

        meterRegistry.counter(METRIC_HISTORY_TIMEOUTS).increment();
    }

    private Map<String, Object> replaySessionResponse(SpotGexQueryService.ReplaySessionResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("ticker", result.ticker());
        response.put("stored", result.stored());
        response.put("requestedDate", result.requestedDate());
        response.put("marketDate", result.marketDate());
        response.put("timeZone", result.timeZone());
        response.put("openAt", result.openAt());
        response.put("closeAt", result.closeAt());
        response.put("latestBucketStart", result.latestBucketStart());
        response.put("availableBucketStarts", result.availableBucketStarts());
        response.put("hasGaps", result.hasGaps());
        response.put("degraded", result.degraded());
        response.put("code", result.code());
        response.put("message", result.message());
        return response;
    }

    private Map<String, Object> replaySnapshotResponse(SpotGexQueryService.ReplaySnapshotResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("ticker", result.ticker());
        response.put("source", result.source());
        response.put("snapshotAt", result.snapshotAt());
        response.put("marketDate", result.marketDate());
        response.put("timeZone", result.timeZone());
        response.put("rowCount", result.rowCount());
        response.put("availableExpiryCount", result.availableExpiryCount());
        response.put("availableStrikeCount", result.availableStrikeCount());
        response.put("data", result.data());
        response.put("degraded", result.degraded());
        response.put("code", result.code());
        response.put("message", result.message());
        return response;
    }

    private ResponseEntity<Map<String, Object>> invalidReplayRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "success", false,
            "degraded", true,
            "code", "INVALID_SPOT_GEX_REPLAY_REQUEST",
            "message", exception.getMessage()
        ));
    }

    private ResponseEntity<Map<String, Object>> replayTimeout(RuntimeException exception) {
        log.warn("[SPOT-GEX] Replay query timed out: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "success", false,
            "degraded", true,
            "code", "SPOT_GEX_REPLAY_TIMEOUT",
            "message", REPLAY_TIMEOUT_MESSAGE
        ));
    }

    private static void putIfPresent(Map<String, Object> response, String key, Object value) {
        if (value != null) {
            response.put(key, value);
        }
    }

    private static String normalizeMetricValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.trim().toUpperCase(Locale.US);
    }
}
