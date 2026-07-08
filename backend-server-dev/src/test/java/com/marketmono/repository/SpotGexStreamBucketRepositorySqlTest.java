package com.marketmono.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexStreamBucketRepositorySqlTest {

    @Test
    void streamLatestSqlShouldReadCurrentSnapshotTable() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findSnapshotSql())
            .contains("spot_gex_strike_expiry_current")
            .contains("bucket_start")
            .contains("latest_timestamp_ms")
            .doesNotContain("spot_gex_exposures");
    }

    @Test
    void streamLatestSqlShouldReadMaintainedLatestRowPerStrikeExpiry() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findSnapshotSql())
            .contains("strike_price AS strike")
            .contains("bucket_start <= ?")
            .contains("bucket_start >= ?")
            .doesNotContain("DISTINCT ON")
            .doesNotContain("spot_gex_strike_expiry_1m");
    }

    @Test
    void streamStatusSqlShouldAvoidAppendOnlyBucketScans() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findStatusSql())
            .contains("spot_gex_strike_expiry_current")
            .contains("MAX(c.bucket_start)")
            .contains("COUNT(*)::bigint AS current_rows")
            .contains("0::bigint AS rows_latest_session")
            .doesNotContain("spot_gex_strike_expiry_1m")
            .doesNotContain("date_trunc")
            .doesNotContain("CURRENT_DATE");
    }

    @Test
    void streamLatestSqlShouldPreserveKnownStreamMetricNullsForRestEnrichment() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findSnapshotSql())
            .contains("call_gamma_oi")
            .contains("put_gamma_oi")
            .contains("call_gamma_bid")
            .contains("call_gamma_ask")
            .contains("put_gamma_bid")
            .contains("put_gamma_ask")
            .doesNotContain("COALESCE(call_gamma_oi, 0)")
            .doesNotContain("COALESCE(put_gamma_oi, 0)")
            .doesNotContain("COALESCE(call_gamma_bid, 0)")
            .doesNotContain("COALESCE(call_gamma_ask, 0)")
            .doesNotContain("COALESCE(put_gamma_bid, 0)")
            .doesNotContain("COALESCE(put_gamma_ask, 0)")
            .doesNotContain("COALESCE(call_delta_oi, 0)");
    }

    @Test
    void streamHistorySqlShouldSupportPlainPostgresDateBinBuckets() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findHistorySql())
            .contains("date_bin")
            .contains("spot_gex_strike_expiry_1m")
            .doesNotContain("time_bucket(")
            .doesNotContain("last(");
    }

    @Test
    void replaySessionSqlShouldListStoredOneMinuteBucketsInsideSessionBounds() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findReplaySessionBucketsSql())
            .contains("SELECT DISTINCT bucket_start")
            .contains("FROM spot_gex_strike_expiry_1m")
            .contains("ticker = ?")
            .contains("bucket_start >= ?")
            .contains("bucket_start <= ?")
            .contains("ORDER BY bucket_start ASC")
            .doesNotContain("spot_gex_strike_expiry_current");
    }

    @Test
    void replaySnapshotSqlShouldReconstructLatestRowPerStrikeExpiryAsOfRequestedMinute() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findReplaySnapshotSql())
            .contains("FROM spot_gex_strike_expiry_1m")
            .contains("ticker = ?")
            .contains("DISTINCT ON")
            .contains("s.bucket_start <= ?")
            .contains("s.bucket_start >= ?")
            .contains("s.latest_timestamp_ms DESC")
            .doesNotContain("bucket_start = ?")
            .doesNotContain("spot_gex_strike_expiry_current");
    }

    @Test
    void replaySnapshotSqlShouldProjectNewYorkMarketDate() {
        assertThat(SpotGexStreamBucketRepositoryImpl.findReplaySnapshotSql())
            .contains("AT TIME ZONE 'America/New_York'")
            .doesNotContain("AT TIME ZONE 'UTC' AS DATE");
    }
}
