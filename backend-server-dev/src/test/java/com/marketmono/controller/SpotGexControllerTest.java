package com.marketmono.controller;

import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import com.marketmono.dto.SpotGexStreamStatusDto;
import com.marketmono.entity.SpotGexExposure;
import com.marketmono.service.SpotGexQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexControllerTest {

    @Mock
    private SpotGexQueryService spotGexQueryService;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    @InjectMocks
    private SpotGexController spotGexController;

    @Test
    void shouldReturnLatestSnapshotResponseBody() {
        Instant collectedAt = Instant.parse("2026-04-06T14:31:00Z");
        SpotGexSnapshotDto snapshot = sampleSnapshot(collectedAt);
        when(spotGexQueryService.getLatestSnapshot("SPY", null, null, null))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(collectedAt, List.of(snapshot)));

        ResponseEntity<Map<String, Object>> response = spotGexController.getLatestSnapshot("SPY", null, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("collectedAt")).isEqualTo(collectedAt);
        assertThat(response.getBody().get("source")).isEqualTo("persisted");
        assertThat(response.getBody().get("data")).isEqualTo(List.of(snapshot));
        assertLatestSnapshotCharmOi(response, "2.5000", "-1.7500", "0.7500");
    }

    @Test
    void shouldReturnLatestSnapshotCoverageMetadata() {
        Instant collectedAt = Instant.parse("2026-04-06T14:31:00Z");
        SpotGexSnapshotDto snapshot = sampleSnapshot(collectedAt);
        when(spotGexQueryService.getLatestSnapshot("IBM", null, null, null))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                collectedAt,
                List.of(snapshot),
                "live",
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ));

        ResponseEntity<Map<String, Object>> response = spotGexController.getLatestSnapshot("IBM", null, null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("source")).isEqualTo("live");
        assertThat(response.getBody().get("coverageMode")).isEqualTo("live_fallback");
        assertThat(response.getBody().get("rowCount")).isEqualTo(1);
        assertThat(response.getBody().get("availableExpiryCount")).isEqualTo(1);
        assertThat(response.getBody().get("availableStrikeCount")).isEqualTo(1);
    }

    @Test
    void shouldReturnDegradedLatestSnapshotResponseBody() {
        when(spotGexQueryService.getLatestSnapshot("OXY", null, null, null))
            .thenReturn(SpotGexQueryService.LatestSnapshotResult.degradedLive(
                "SPOT_GEX_OPTION_CHAIN_EMPTY",
                "No option chain expirations are available for OXY."
            ));

        ResponseEntity<Map<String, Object>> response = spotGexController.getLatestSnapshot("OXY", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("source")).isEqualTo("live");
        assertThat(response.getBody().get("degraded")).isEqualTo(true);
        assertThat(response.getBody().get("code")).isEqualTo("SPOT_GEX_OPTION_CHAIN_EMPTY");
        assertThat(response.getBody().get("message")).isEqualTo("No option chain expirations are available for OXY.");
        assertThat(response.getBody().get("data")).isEqualTo(List.of());
    }

    @Test
    void shouldReturnHistoryResponseBody() {
        Instant since = Instant.parse("2026-04-06T14:00:00Z");
        SpotGexSnapshotDto snapshot = sampleSnapshot(Instant.parse("2026-04-06T14:30:00Z"));
        when(spotGexQueryService.getHistoryResult("SPY", since, "5m", null, null, null, null))
            .thenReturn(SpotGexQueryService.HistoryResult.success(List.of(snapshot)));

        ResponseEntity<Map<String, Object>> response = spotGexController.getHistory("SPY", since, "5m", null, null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("bucket")).isEqualTo("5m");
        assertThat(response.getBody().get("data")).isEqualTo(List.of(snapshot));
    }

    @Test
    void shouldReturnDegradedHistoryResponseBody() {
        Instant since = Instant.parse("2026-04-06T14:00:00Z");
        when(spotGexQueryService.getHistoryResult("SPY", since, "5m", null, null, null, null))
            .thenReturn(SpotGexQueryService.HistoryResult.degraded(
                "SPOT_GEX_HISTORY_WINDOW_TOO_WIDE",
                "Use a narrower Spot GEX history range."
            ));

        ResponseEntity<Map<String, Object>> response = spotGexController.getHistory("SPY", since, "5m", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("degraded")).isEqualTo(true);
        assertThat(response.getBody().get("code")).isEqualTo("SPOT_GEX_HISTORY_WINDOW_TOO_WIDE");
        assertThat(response.getBody().get("data")).isEqualTo(List.of());
    }

    @Test
    void shouldReturnReplaySessionResponseBody() {
        LocalDate requestedDate = LocalDate.of(2026, 6, 20);
        LocalDate marketDate = LocalDate.of(2026, 6, 19);
        Instant openAt = Instant.parse("2026-06-19T13:30:00Z");
        Instant closeAt = Instant.parse("2026-06-19T20:15:00Z");
        Instant firstBucket = Instant.parse("2026-06-19T13:30:00Z");
        Instant latestBucket = Instant.parse("2026-06-19T13:31:00Z");
        SpotGexQueryService.ReplaySessionResult result =
            new SpotGexQueryService.ReplaySessionResult(
                "SPY",
                true,
                requestedDate,
                marketDate,
                "America/New_York",
                openAt,
                closeAt,
                latestBucket,
                List.of(firstBucket, latestBucket),
                false,
                false,
                null,
                null
            );
        when(spotGexQueryService.getReplaySession("SPY", requestedDate)).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = spotGexController.getReplaySession("SPY", requestedDate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("ticker")).isEqualTo("SPY");
        assertThat(response.getBody().get("stored")).isEqualTo(true);
        assertThat(response.getBody().get("requestedDate")).isEqualTo(requestedDate);
        assertThat(response.getBody().get("marketDate")).isEqualTo(marketDate);
        assertThat(response.getBody().get("latestBucketStart")).isEqualTo(latestBucket);
        assertThat(response.getBody().get("availableBucketStarts")).isEqualTo(List.of(firstBucket, latestBucket));
        assertThat(response.getBody().get("degraded")).isEqualTo(false);
    }

    @Test
    void shouldReturnReplaySnapshotResponseBody() {
        Instant snapshotAt = Instant.parse("2026-06-19T13:31:00Z");
        LocalDate expiry = LocalDate.of(2026, 6, 19);
        SpotGexSnapshotDto snapshot = sampleSnapshot(snapshotAt);
        SpotGexQueryService.ReplaySnapshotResult result =
            new SpotGexQueryService.ReplaySnapshotResult(
                true,
                "SPY",
                "replay",
                snapshotAt,
                LocalDate.of(2026, 6, 19),
                "America/New_York",
                1,
                1,
                1,
                List.of(snapshot),
                false,
                null,
                null
            );
        when(spotGexQueryService.getReplaySnapshot(
            "SPY",
            snapshotAt,
            expiry,
            new BigDecimal("700"),
            new BigDecimal("750")
        )).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = spotGexController.getReplaySnapshot(
            "SPY",
            snapshotAt,
            expiry,
            new BigDecimal("700"),
            new BigDecimal("750")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("source")).isEqualTo("replay");
        assertThat(response.getBody().get("snapshotAt")).isEqualTo(snapshotAt);
        assertThat(response.getBody().get("rowCount")).isEqualTo(1);
        assertThat(response.getBody().get("data")).isEqualTo(List.of(snapshot));
    }

    @Test
    void shouldReturnNotFoundForMissingReplaySnapshotBucket() {
        Instant snapshotAt = Instant.parse("2026-06-19T13:31:00Z");
        SpotGexQueryService.ReplaySnapshotResult result =
            new SpotGexQueryService.ReplaySnapshotResult(
                false,
                "SPY",
                "replay",
                snapshotAt,
                null,
                "America/New_York",
                0,
                0,
                0,
                List.of(),
                true,
                "SPOT_GEX_REPLAY_BUCKET_MISSING",
                "No stored Spot GEX replay bucket is available for SPY."
            );
        when(spotGexQueryService.getReplaySnapshot("SPY", snapshotAt, null, null, null)).thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
            spotGexController.getReplaySnapshot("SPY", snapshotAt, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
        assertThat(response.getBody().get("degraded")).isEqualTo(true);
        assertThat(response.getBody().get("code")).isEqualTo("SPOT_GEX_REPLAY_BUCKET_MISSING");
    }

    @Test
    void shouldReturnCollectionStatusResponseBody() {
        SpotGexCollectionStatusDto status = new SpotGexCollectionStatusDto(
            "SPY",
            Instant.parse("2026-04-06T14:31:00Z"),
            42,
            3,
            Instant.parse("2026-04-06T14:31:00Z"),
            Instant.parse("2026-04-06T14:31:00Z"),
            null,
            0,
            true,
            true
        );
        SpotGexStreamStatusDto streamStatus = new SpotGexStreamStatusDto(
            "stream",
            "backend-a-prod",
            "backend-a-prod",
            true,
            "options_feed_ws",
            true,
            true,
            1,
            0,
            List.of("SPY"),
            Instant.parse("2026-06-11T14:32:56Z"),
            "SPY",
            Instant.parse("2026-06-11T14:33:00Z"),
            Instant.parse("2026-06-11T14:32:00Z"),
            60_000L,
            false,
            120_000L,
            0,
            List.of(new SpotGexStreamStatusDto.TickerStatus(
                "SPY",
                true,
                Instant.parse("2026-06-11T14:32:00Z"),
                60_000L,
                false
            ))
        );
        when(spotGexQueryService.getCollectionStatus()).thenReturn(List.of(status));
        when(spotGexQueryService.getStreamStatus()).thenReturn(streamStatus);

        ResponseEntity<Map<String, Object>> response = spotGexController.getCollectionStatus();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("data")).isEqualTo(List.of(status));
        assertThat(response.getBody().get("stream")).isEqualTo(streamStatus);
    }

    @Test
    void shouldReturnServiceUnavailableForHistoryTimeout() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);

        ResponseEntity<Map<String, Object>> response =
            spotGexController.handleSpotGexHistoryTimeout(new QueryTimeoutException("too slow"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
        assertThat(response.getBody().get("code")).isEqualTo("SPOT_GEX_HISTORY_TIMEOUT");
        assertThat(meterRegistry.get("spot_gex.history.timeouts").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void shouldReturnServiceUnavailableForStatementCancellation() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);

        ResponseEntity<Map<String, Object>> response =
            spotGexController.handleSpotGexHistoryTimeout(
                new DataAccessResourceFailureException("PreparedStatementCallback; ERROR: canceling statement due to user request")
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("SPOT_GEX_HISTORY_TIMEOUT");
        assertThat(meterRegistry.get("spot_gex.history.timeouts").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void shouldReturnBadRequestForInvalidHistoryRequest() {
        ResponseEntity<Map<String, Object>> response =
            spotGexController.handleInvalidSpotGexHistoryRequest(new IllegalArgumentException("Unsupported bucket: 30m"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(false);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_SPOT_GEX_HISTORY_REQUEST");
        assertThat(response.getBody().get("message")).isEqualTo("Unsupported bucket: 30m");
    }

    private SpotGexSnapshotDto sampleSnapshot(Instant snapshotAt) {
        return SpotGexSnapshotDto.from(SpotGexExposure.builder()
            .ticker("SPY")
            .expiry(LocalDate.of(2026, 4, 17))
            .strike(new BigDecimal("500.0000"))
            .collectedAt(snapshotAt)
            .price(new BigDecimal("502.1200"))
            .marketDate(LocalDate.of(2026, 4, 6))
            .sourceTime(snapshotAt)
            .callGammaOi(BigDecimal.ONE)
            .callCharmOi(new BigDecimal("2.5000"))
            .putCharmOi(new BigDecimal("-1.7500"))
            .build());
    }

    @SuppressWarnings("unchecked")
    private static void assertLatestSnapshotCharmOi(
            ResponseEntity<Map<String, Object>> response,
            String expectedCallCharmOi,
            String expectedPutCharmOi,
            String expectedNetCharmOi) {
        List<SpotGexSnapshotDto> data = (List<SpotGexSnapshotDto>) response.getBody().get("data");

        assertThat(data).singleElement().satisfies(row -> {
            assertThat(row.callCharmOi()).isEqualByComparingTo(expectedCallCharmOi);
            assertThat(row.putCharmOi()).isEqualByComparingTo(expectedPutCharmOi);
            assertThat(row.callCharmOi().add(row.putCharmOi())).isEqualByComparingTo(expectedNetCharmOi);
        });
    }
}
