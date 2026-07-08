package com.marketmono.service.spotgex;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexStreamBucketPersistenceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private SpotGexStreamBucketPersistence persistence;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        persistence = new SpotGexStreamBucketPersistence(jdbcTemplate, meterRegistry);
        persistence.initMetrics();
    }

    @Test
    void shouldUpsertStreamBucketsAndTrackOlderReplaysSkippedByTimestampGuard() {
        List<SpotGexStreamBucket> buckets = List.of(
            bucket(1_781_198_376_000L, "250.0000", "-75.0000"),
            bucket(1_781_198_350_000L, "100.0000", "-25.0000")
        );

        when(jdbcTemplate.batchUpdate(anyString(), eq(buckets), eq(buckets.size()), any()))
            .thenReturn(new int[][]{{1, 0}});

        SpotGexStreamBucketPersistence.BatchPersistResult result = persistence.persistBuckets(buckets);

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.skippedOlderReplay()).isEqualTo(1);
        assertThat(meterRegistry.get("spot_gex.stream.buckets.upserted").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.stream.buckets.replays_skipped").counter().count()).isEqualTo(1.0d);
        verify(jdbcTemplate).batchUpdate(eq(SpotGexStreamBucketPersistence.upsertSql()), eq(buckets), eq(buckets.size()), any());
        verify(jdbcTemplate).batchUpdate(eq(SpotGexStreamBucketPersistence.currentUpsertSql()), eq(buckets), eq(buckets.size()), any());
    }

    @Test
    void shouldSkipJdbcCallWhenNoBucketsAreReady() {
        SpotGexStreamBucketPersistence.BatchPersistResult result = persistence.persistBuckets(List.of());

        assertThat(result.attempted()).isZero();
        assertThat(result.upserted()).isZero();
        assertThat(result.skippedOlderReplay()).isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(), anyInt(), any());
    }

    @Test
    void upsertSqlShouldPreserveLatestTimestampSemanticsAndComputeNetFields() {
        String sql = SpotGexStreamBucketPersistence.upsertSql();

        assertThat(sql)
            .contains("INSERT INTO spot_gex_strike_expiry_1m")
            .contains("ON CONFLICT (ticker, expiry, strike_price_cents, bucket_start) DO UPDATE")
            .contains("latest_timestamp_ms <= EXCLUDED.latest_timestamp_ms")
            .contains("net_gamma_oi")
            .contains("net_gamma_vol")
            .doesNotContain("DO NOTHING");
    }

    @Test
    void currentUpsertSqlShouldMaintainOneLatestRowPerTickerExpiryStrike() {
        String sql = SpotGexStreamBucketPersistence.currentUpsertSql();

        assertThat(sql)
            .contains("INSERT INTO spot_gex_strike_expiry_current")
            .contains("ON CONFLICT (ticker, expiry, strike_price_cents) DO UPDATE")
            .contains("bucket_start = EXCLUDED.bucket_start")
            .contains("latest_timestamp_ms <= EXCLUDED.latest_timestamp_ms")
            .contains("net_charm_oi")
            .doesNotContain("spot_gex_strike_expiry_1m");
    }

    private static SpotGexStreamBucket bucket(long latestTimestampMs, String callGammaOi, String putGammaOi) {
        return SpotGexStreamBucket.fromLatestEvent(SpotGexStreamEvent.builder()
            .ticker("SPY")
            .expiry(LocalDate.of(2026, 6, 11))
            .strikePrice(new BigDecimal("725"))
            .strikePriceCents(72_500)
            .timestampMs(latestTimestampMs)
            .bucketStartMs(1_781_198_340_000L)
            .ingestedAtMs(latestTimestampMs + 100L)
            .underlyingPrice(new BigDecimal("725.6750"))
            .callGammaOi(new BigDecimal(callGammaOi))
            .putGammaOi(new BigDecimal(putGammaOi))
            .callGammaVol(new BigDecimal("1000.0000"))
            .putGammaVol(new BigDecimal("-300.0000"))
            .source("options_feed_ws")
            .rawChannelName("gex_strike_expiry:SPY")
            .build());
    }
}
