package com.marketmono.service.spotgex;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotGexStreamBucketPersistence {

    private static final String UPSERT_SQL = """
        INSERT INTO spot_gex_strike_expiry_1m (
            ticker, expiry, strike_price, strike_price_cents, bucket_start,
            latest_timestamp_ms, underlying_price,
            call_delta_oi, put_delta_oi, net_delta_oi,
            call_gamma_oi, put_gamma_oi, net_gamma_oi,
            call_charm_oi, put_charm_oi, net_charm_oi,
            call_vanna_oi, put_vanna_oi, net_vanna_oi,
            call_delta_vol, put_delta_vol, net_delta_vol,
            call_gamma_vol, put_gamma_vol, net_gamma_vol,
            call_charm_vol, put_charm_vol, net_charm_vol,
            call_vanna_vol, put_vanna_vol, net_vanna_vol,
            call_gamma_ask, call_gamma_bid, put_gamma_ask, put_gamma_bid,
            call_charm_ask, call_charm_bid, put_charm_ask, put_charm_bid,
            call_vanna_ask, call_vanna_bid, put_vanna_ask, put_vanna_bid,
            source, raw_channel_name, ingested_at, updated_at
        ) VALUES (
            ?, ?, ?, ?, ?,
            ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, NOW()
        )
        ON CONFLICT (ticker, expiry, strike_price_cents, bucket_start) DO UPDATE SET
            strike_price = EXCLUDED.strike_price,
            latest_timestamp_ms = EXCLUDED.latest_timestamp_ms,
            underlying_price = EXCLUDED.underlying_price,
            call_delta_oi = EXCLUDED.call_delta_oi,
            put_delta_oi = EXCLUDED.put_delta_oi,
            net_delta_oi = EXCLUDED.net_delta_oi,
            call_gamma_oi = EXCLUDED.call_gamma_oi,
            put_gamma_oi = EXCLUDED.put_gamma_oi,
            net_gamma_oi = EXCLUDED.net_gamma_oi,
            call_charm_oi = EXCLUDED.call_charm_oi,
            put_charm_oi = EXCLUDED.put_charm_oi,
            net_charm_oi = EXCLUDED.net_charm_oi,
            call_vanna_oi = EXCLUDED.call_vanna_oi,
            put_vanna_oi = EXCLUDED.put_vanna_oi,
            net_vanna_oi = EXCLUDED.net_vanna_oi,
            call_delta_vol = EXCLUDED.call_delta_vol,
            put_delta_vol = EXCLUDED.put_delta_vol,
            net_delta_vol = EXCLUDED.net_delta_vol,
            call_gamma_vol = EXCLUDED.call_gamma_vol,
            put_gamma_vol = EXCLUDED.put_gamma_vol,
            net_gamma_vol = EXCLUDED.net_gamma_vol,
            call_charm_vol = EXCLUDED.call_charm_vol,
            put_charm_vol = EXCLUDED.put_charm_vol,
            net_charm_vol = EXCLUDED.net_charm_vol,
            call_vanna_vol = EXCLUDED.call_vanna_vol,
            put_vanna_vol = EXCLUDED.put_vanna_vol,
            net_vanna_vol = EXCLUDED.net_vanna_vol,
            call_gamma_ask = EXCLUDED.call_gamma_ask,
            call_gamma_bid = EXCLUDED.call_gamma_bid,
            put_gamma_ask = EXCLUDED.put_gamma_ask,
            put_gamma_bid = EXCLUDED.put_gamma_bid,
            call_charm_ask = EXCLUDED.call_charm_ask,
            call_charm_bid = EXCLUDED.call_charm_bid,
            put_charm_ask = EXCLUDED.put_charm_ask,
            put_charm_bid = EXCLUDED.put_charm_bid,
            call_vanna_ask = EXCLUDED.call_vanna_ask,
            call_vanna_bid = EXCLUDED.call_vanna_bid,
            put_vanna_ask = EXCLUDED.put_vanna_ask,
            put_vanna_bid = EXCLUDED.put_vanna_bid,
            source = EXCLUDED.source,
            raw_channel_name = EXCLUDED.raw_channel_name,
            ingested_at = EXCLUDED.ingested_at,
            updated_at = NOW()
        WHERE spot_gex_strike_expiry_1m.latest_timestamp_ms <= EXCLUDED.latest_timestamp_ms
        """;

    private static final String CURRENT_UPSERT_SQL = """
        INSERT INTO spot_gex_strike_expiry_current (
            ticker, expiry, strike_price, strike_price_cents, bucket_start,
            latest_timestamp_ms, underlying_price,
            call_delta_oi, put_delta_oi, net_delta_oi,
            call_gamma_oi, put_gamma_oi, net_gamma_oi,
            call_charm_oi, put_charm_oi, net_charm_oi,
            call_vanna_oi, put_vanna_oi, net_vanna_oi,
            call_delta_vol, put_delta_vol, net_delta_vol,
            call_gamma_vol, put_gamma_vol, net_gamma_vol,
            call_charm_vol, put_charm_vol, net_charm_vol,
            call_vanna_vol, put_vanna_vol, net_vanna_vol,
            call_gamma_ask, call_gamma_bid, put_gamma_ask, put_gamma_bid,
            call_charm_ask, call_charm_bid, put_charm_ask, put_charm_bid,
            call_vanna_ask, call_vanna_bid, put_vanna_ask, put_vanna_bid,
            source, raw_channel_name, ingested_at, updated_at
        ) VALUES (
            ?, ?, ?, ?, ?,
            ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, NOW()
        )
        ON CONFLICT (ticker, expiry, strike_price_cents) DO UPDATE SET
            strike_price = EXCLUDED.strike_price,
            bucket_start = EXCLUDED.bucket_start,
            latest_timestamp_ms = EXCLUDED.latest_timestamp_ms,
            underlying_price = EXCLUDED.underlying_price,
            call_delta_oi = EXCLUDED.call_delta_oi,
            put_delta_oi = EXCLUDED.put_delta_oi,
            net_delta_oi = EXCLUDED.net_delta_oi,
            call_gamma_oi = EXCLUDED.call_gamma_oi,
            put_gamma_oi = EXCLUDED.put_gamma_oi,
            net_gamma_oi = EXCLUDED.net_gamma_oi,
            call_charm_oi = EXCLUDED.call_charm_oi,
            put_charm_oi = EXCLUDED.put_charm_oi,
            net_charm_oi = EXCLUDED.net_charm_oi,
            call_vanna_oi = EXCLUDED.call_vanna_oi,
            put_vanna_oi = EXCLUDED.put_vanna_oi,
            net_vanna_oi = EXCLUDED.net_vanna_oi,
            call_delta_vol = EXCLUDED.call_delta_vol,
            put_delta_vol = EXCLUDED.put_delta_vol,
            net_delta_vol = EXCLUDED.net_delta_vol,
            call_gamma_vol = EXCLUDED.call_gamma_vol,
            put_gamma_vol = EXCLUDED.put_gamma_vol,
            net_gamma_vol = EXCLUDED.net_gamma_vol,
            call_charm_vol = EXCLUDED.call_charm_vol,
            put_charm_vol = EXCLUDED.put_charm_vol,
            net_charm_vol = EXCLUDED.net_charm_vol,
            call_vanna_vol = EXCLUDED.call_vanna_vol,
            put_vanna_vol = EXCLUDED.put_vanna_vol,
            net_vanna_vol = EXCLUDED.net_vanna_vol,
            call_gamma_ask = EXCLUDED.call_gamma_ask,
            call_gamma_bid = EXCLUDED.call_gamma_bid,
            put_gamma_ask = EXCLUDED.put_gamma_ask,
            put_gamma_bid = EXCLUDED.put_gamma_bid,
            call_charm_ask = EXCLUDED.call_charm_ask,
            call_charm_bid = EXCLUDED.call_charm_bid,
            put_charm_ask = EXCLUDED.put_charm_ask,
            put_charm_bid = EXCLUDED.put_charm_bid,
            call_vanna_ask = EXCLUDED.call_vanna_ask,
            call_vanna_bid = EXCLUDED.call_vanna_bid,
            put_vanna_ask = EXCLUDED.put_vanna_ask,
            put_vanna_bid = EXCLUDED.put_vanna_bid,
            source = EXCLUDED.source,
            raw_channel_name = EXCLUDED.raw_channel_name,
            ingested_at = EXCLUDED.ingested_at,
            updated_at = NOW()
        WHERE spot_gex_strike_expiry_current.latest_timestamp_ms <= EXCLUDED.latest_timestamp_ms
        """;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private Counter upsertedCounter;
    private Counter replaySkippedCounter;
    private Counter failedCounter;
    private Timer batchTimer;

    static String upsertSql() {
        return UPSERT_SQL;
    }

    static String currentUpsertSql() {
        return CURRENT_UPSERT_SQL;
    }

    @PostConstruct
    public void initMetrics() {
        upsertedCounter = meterRegistry.counter("spot_gex.stream.buckets.upserted");
        replaySkippedCounter = meterRegistry.counter("spot_gex.stream.buckets.replays_skipped");
        failedCounter = meterRegistry.counter("spot_gex.stream.buckets.failed");
        batchTimer = meterRegistry.timer("spot_gex.stream.buckets.batch_persist");
    }

    @Transactional
    public BatchPersistResult persistBuckets(List<SpotGexStreamBucket> buckets) {
        if (buckets.isEmpty()) {
            return new BatchPersistResult(0, 0, 0);
        }

        try {
            int[][] results = batchTimer.record(() -> jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                buckets,
                buckets.size(),
                this::setParameters
            ));
            jdbcTemplate.batchUpdate(
                CURRENT_UPSERT_SQL,
                buckets,
                buckets.size(),
                this::setParameters
            );

            int upserted = countUpdated(results);
            int skipped = buckets.size() - upserted;
            upsertedCounter.increment(upserted);
            if (skipped > 0) {
                replaySkippedCounter.increment(skipped);
            }

            return new BatchPersistResult(buckets.size(), upserted, skipped);
        } catch (Exception exception) {
            failedCounter.increment(buckets.size());
            log.error("[SPOT-GEX] Stream bucket persistence failed for {} buckets: {}",
                buckets.size(), exception.getMessage(), exception);
            throw exception;
        }
    }

    private void setParameters(PreparedStatement statement, SpotGexStreamBucket bucket) throws SQLException {
        statement.setString(1, bucket.ticker());
        statement.setDate(2, Date.valueOf(bucket.expiry()));
        statement.setBigDecimal(3, bucket.strikePrice());
        statement.setInt(4, bucket.strikePriceCents());
        statement.setTimestamp(5, timestamp(bucket.bucketStartMs()));
        statement.setLong(6, bucket.latestTimestampMs());
        setNullableBigDecimal(statement, 7, bucket.underlyingPrice());
        setNullableBigDecimal(statement, 8, bucket.callDeltaOi());
        setNullableBigDecimal(statement, 9, bucket.putDeltaOi());
        setNullableBigDecimal(statement, 10, bucket.netDeltaOi());
        setNullableBigDecimal(statement, 11, bucket.callGammaOi());
        setNullableBigDecimal(statement, 12, bucket.putGammaOi());
        setNullableBigDecimal(statement, 13, bucket.netGammaOi());
        setNullableBigDecimal(statement, 14, bucket.callCharmOi());
        setNullableBigDecimal(statement, 15, bucket.putCharmOi());
        setNullableBigDecimal(statement, 16, bucket.netCharmOi());
        setNullableBigDecimal(statement, 17, bucket.callVannaOi());
        setNullableBigDecimal(statement, 18, bucket.putVannaOi());
        setNullableBigDecimal(statement, 19, bucket.netVannaOi());
        setNullableBigDecimal(statement, 20, bucket.callDeltaVol());
        setNullableBigDecimal(statement, 21, bucket.putDeltaVol());
        setNullableBigDecimal(statement, 22, bucket.netDeltaVol());
        setNullableBigDecimal(statement, 23, bucket.callGammaVol());
        setNullableBigDecimal(statement, 24, bucket.putGammaVol());
        setNullableBigDecimal(statement, 25, bucket.netGammaVol());
        setNullableBigDecimal(statement, 26, bucket.callCharmVol());
        setNullableBigDecimal(statement, 27, bucket.putCharmVol());
        setNullableBigDecimal(statement, 28, bucket.netCharmVol());
        setNullableBigDecimal(statement, 29, bucket.callVannaVol());
        setNullableBigDecimal(statement, 30, bucket.putVannaVol());
        setNullableBigDecimal(statement, 31, bucket.netVannaVol());
        setNullableBigDecimal(statement, 32, bucket.callGammaAsk());
        setNullableBigDecimal(statement, 33, bucket.callGammaBid());
        setNullableBigDecimal(statement, 34, bucket.putGammaAsk());
        setNullableBigDecimal(statement, 35, bucket.putGammaBid());
        setNullableBigDecimal(statement, 36, bucket.callCharmAsk());
        setNullableBigDecimal(statement, 37, bucket.callCharmBid());
        setNullableBigDecimal(statement, 38, bucket.putCharmAsk());
        setNullableBigDecimal(statement, 39, bucket.putCharmBid());
        setNullableBigDecimal(statement, 40, bucket.callVannaAsk());
        setNullableBigDecimal(statement, 41, bucket.callVannaBid());
        setNullableBigDecimal(statement, 42, bucket.putVannaAsk());
        setNullableBigDecimal(statement, 43, bucket.putVannaBid());
        statement.setString(44, bucket.source());
        statement.setString(45, bucket.rawChannelName());
        statement.setTimestamp(46, timestamp(bucket.ingestedAtMs()));
    }

    private Timestamp timestamp(long epochMs) {
        return Timestamp.from(Instant.ofEpochMilli(epochMs));
    }

    private static void setNullableBigDecimal(PreparedStatement statement, int index, BigDecimal value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
            return;
        }
        statement.setBigDecimal(index, value);
    }

    private static int countUpdated(int[][] results) {
        int count = 0;
        for (int[] batch : results) {
            for (int result : batch) {
                if (result > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public record BatchPersistResult(int attempted, int upserted, int skippedOlderReplay) {}
}
