package com.marketmono.service;

import com.marketmono.entity.SpotGexExposure;
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
import java.time.LocalDate;
import java.util.List;

/**
 * High-throughput JDBC batch persistence for scheduled spot GEX snapshots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpotGexExposureBatchPersistence {

    private static final String UPSERT_SQL = """
        INSERT INTO spot_gex_exposures (
            ticker, expiry, strike, collected_at,
            price, date, source_time,
            call_delta_oi, call_gamma_oi, call_vanna_oi, call_charm_oi,
            put_delta_oi, put_gamma_oi, put_vanna_oi, put_charm_oi,
            call_delta_vol, call_gamma_vol, call_vanna_vol, call_charm_vol,
            put_delta_vol, put_gamma_vol, put_vanna_vol, put_charm_vol,
            call_delta_bid, call_delta_ask, call_gamma_bid, call_gamma_ask,
            call_vanna_bid, call_vanna_ask, call_charm_bid, call_charm_ask,
            put_delta_bid, put_delta_ask, put_gamma_bid, put_gamma_ask,
            put_vanna_bid, put_vanna_ask, put_charm_bid, put_charm_ask
        ) VALUES (
            ?, ?, ?, ?,
            ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?,
            ?, ?, ?, ?
        )
        ON CONFLICT (ticker, expiry, strike, collected_at) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private Counter persistedCounter;
    private Counter duplicateCounter;
    private Counter failedCounter;
    private Timer batchTimer;

    @PostConstruct
    public void initMetrics() {
        persistedCounter = meterRegistry.counter("spot_gex.persisted");
        duplicateCounter = meterRegistry.counter("spot_gex.duplicates_skipped");
        failedCounter = meterRegistry.counter("spot_gex.failed");
        batchTimer = meterRegistry.timer("spot_gex.batch_persist");
        log.info("[SPOT-GEX] SpotGexExposureBatchPersistence initialized");
    }

    @Transactional
    public BatchPersistResult persistBatch(List<SpotGexExposure> exposures) {
        if (exposures.isEmpty()) {
            return new BatchPersistResult(0, 0, 0);
        }

        long start = System.currentTimeMillis();

        try {
            int[][] results = batchTimer.record(() -> jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                exposures,
                exposures.size(),
                this::setParameters
            ));

            int inserted = countInserted(results);
            int skipped = exposures.size() - inserted;

            persistedCounter.increment(inserted);
            if (skipped > 0) {
                duplicateCounter.increment(skipped);
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("[SPOT-GEX] Persisted batch: attempted={}, inserted={}, skipped={}, elapsedMs={}",
                exposures.size(), inserted, skipped, elapsed);

            return new BatchPersistResult(exposures.size(), inserted, skipped);
        } catch (Exception e) {
            failedCounter.increment(exposures.size());
            long elapsed = System.currentTimeMillis() - start;
            log.error("[SPOT-GEX] Batch persistence failed after {}ms for {} rows: {}",
                elapsed, exposures.size(), e.getMessage(), e);
            throw e;
        }
    }

    private void setParameters(PreparedStatement ps, SpotGexExposure exposure) throws SQLException {
        ps.setString(1, exposure.getTicker());
        ps.setDate(2, Date.valueOf(exposure.getExpiry()));
        ps.setBigDecimal(3, exposure.getStrike());
        ps.setTimestamp(4, Timestamp.from(exposure.getCollectedAt()));

        setNullableBigDecimal(ps, 5, exposure.getPrice());
        setNullableLocalDate(ps, 6, exposure.getMarketDate());
        setNullableInstant(ps, 7, exposure.getSourceTime());

        setNullableBigDecimal(ps, 8, exposure.getCallDeltaOi());
        setNullableBigDecimal(ps, 9, exposure.getCallGammaOi());
        setNullableBigDecimal(ps, 10, exposure.getCallVannaOi());
        setNullableBigDecimal(ps, 11, exposure.getCallCharmOi());

        setNullableBigDecimal(ps, 12, exposure.getPutDeltaOi());
        setNullableBigDecimal(ps, 13, exposure.getPutGammaOi());
        setNullableBigDecimal(ps, 14, exposure.getPutVannaOi());
        setNullableBigDecimal(ps, 15, exposure.getPutCharmOi());

        setNullableBigDecimal(ps, 16, exposure.getCallDeltaVol());
        setNullableBigDecimal(ps, 17, exposure.getCallGammaVol());
        setNullableBigDecimal(ps, 18, exposure.getCallVannaVol());
        setNullableBigDecimal(ps, 19, exposure.getCallCharmVol());

        setNullableBigDecimal(ps, 20, exposure.getPutDeltaVol());
        setNullableBigDecimal(ps, 21, exposure.getPutGammaVol());
        setNullableBigDecimal(ps, 22, exposure.getPutVannaVol());
        setNullableBigDecimal(ps, 23, exposure.getPutCharmVol());

        setNullableBigDecimal(ps, 24, exposure.getCallDeltaBid());
        setNullableBigDecimal(ps, 25, exposure.getCallDeltaAsk());
        setNullableBigDecimal(ps, 26, exposure.getCallGammaBid());
        setNullableBigDecimal(ps, 27, exposure.getCallGammaAsk());
        setNullableBigDecimal(ps, 28, exposure.getCallVannaBid());
        setNullableBigDecimal(ps, 29, exposure.getCallVannaAsk());
        setNullableBigDecimal(ps, 30, exposure.getCallCharmBid());
        setNullableBigDecimal(ps, 31, exposure.getCallCharmAsk());

        setNullableBigDecimal(ps, 32, exposure.getPutDeltaBid());
        setNullableBigDecimal(ps, 33, exposure.getPutDeltaAsk());
        setNullableBigDecimal(ps, 34, exposure.getPutGammaBid());
        setNullableBigDecimal(ps, 35, exposure.getPutGammaAsk());
        setNullableBigDecimal(ps, 36, exposure.getPutVannaBid());
        setNullableBigDecimal(ps, 37, exposure.getPutVannaAsk());
        setNullableBigDecimal(ps, 38, exposure.getPutCharmBid());
        setNullableBigDecimal(ps, 39, exposure.getPutCharmAsk());
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }

    private static void setNullableInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    private static void setNullableLocalDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value != null) {
            ps.setDate(index, Date.valueOf(value));
        } else {
            ps.setNull(index, Types.DATE);
        }
    }

    private static int countInserted(int[][] results) {
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

    public record BatchPersistResult(int attempted, int inserted, int skipped) {}
}
