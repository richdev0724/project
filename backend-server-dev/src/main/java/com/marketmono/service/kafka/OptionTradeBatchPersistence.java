package com.marketmono.service.kafka;

import com.marketmono.config.KafkaConfig;
import com.marketmono.entity.OptionTrade;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

/**
 * High-throughput batch persistence service for Kafka-ingested option trades.
 * Designed for 6-10M inserts/day with minimal overhead.
 *
 * <p>Uses JDBC batch INSERT ... ON CONFLICT DO NOTHING to make inserts idempotent.
 * This is critical for Kafka at-least-once delivery: after a crash between DB commit
 * and offset acknowledgment, Kafka redelivers the batch. Without ON CONFLICT,
 * duplicate PKs would cause the entire batch to fail and go to DLT, losing new records.
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class OptionTradeBatchPersistence {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaConfig kafkaConfig;

    private Timer batchTimer;

    private static final String UPSERT_SQL = """
        INSERT INTO option_trades (
            trade_id, ticker, price, size, nbbo_bid, nbbo_ask,
            underlying_price, exchange, executed_at, option_chain,
            condition, is_multileg, is_floor_trade, is_sweep,
            aggressor, premium, side, created_at,
            expiry, option_type, open_interest, strike, volume,
            ewma_nbbo_ask, ewma_nbbo_bid, implied_volatility,
            delta, theta, gamma, vega, rho, theo,
            ask_vol, bid_vol, no_side_vol, mid_vol, multi_vol, stock_multi_vol
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (trade_id, executed_at) DO NOTHING
        """;

    @PostConstruct
    public void initMetrics() {
        meterRegistry.counter("kafka.option_trades.persisted");
        meterRegistry.counter("kafka.option_trades.duplicates_skipped");
        meterRegistry.counter("kafka.option_trades.failed");
        batchTimer = meterRegistry.timer("kafka.option_trades.batch_persist");
        log.info("[KAFKA] OptionTradeBatchPersistence initialized — using JDBC batch inserts with ON CONFLICT DO NOTHING");
    }

    /**
     * Persist a batch of option trades in a single transaction.
     * Uses JDBC batch inserts with ON CONFLICT DO NOTHING for idempotent ingestion.
     * Duplicate trades (same trade_id + executed_at) are silently skipped.
     */
    @Transactional
    public void persistBatch(List<OptionTrade> trades) {
        if (trades.isEmpty()) return;

        long start = System.currentTimeMillis();

        batchTimer.record(() -> {
            try {
                int[][] results = jdbcTemplate.batchUpdate(
                    UPSERT_SQL,
                    trades,
                    jdbcBatchSize(),
                    this::setParameters
                );

                int inserted = countInserted(results);
                int skipped = trades.size() - inserted;

                meterRegistry.counter("kafka.option_trades.persisted").increment(inserted);
                if (skipped > 0) {
                    meterRegistry.counter("kafka.option_trades.duplicates_skipped").increment(skipped);
                    log.debug("[KAFKA] Batch: {} inserted, {} duplicates skipped", inserted, skipped);
                }

                long elapsed = System.currentTimeMillis() - start;
                log.debug("[KAFKA] Batch persisted: {}/{} trades in {}ms (avg {}µs/trade)",
                    inserted, trades.size(), elapsed,
                    inserted > 0 ? (elapsed * 1000L / inserted) : 0);
            } catch (Exception e) {
                meterRegistry.counter("kafka.option_trades.failed").increment(trades.size());
                long elapsed = System.currentTimeMillis() - start;
                log.error("[KAFKA] Batch persistence FAILED after {}ms for {} trades: {}",
                    elapsed, trades.size(), e.getMessage(), e);
                throw e;
            }
        });
    }

    private int jdbcBatchSize() {
        return Math.max(1, kafkaConfig.getConsumer().getBatchSize());
    }

    private void setParameters(PreparedStatement ps, OptionTrade t) throws SQLException {
        ps.setObject(1, t.getTradeId());                                     // trade_id
        ps.setString(2, t.getTicker());                                      // ticker
        ps.setBigDecimal(3, t.getPrice());                                   // price
        ps.setInt(4, t.getSize());                                           // size
        setNullableBigDecimal(ps, 5, t.getNbboBid());                        // nbbo_bid
        setNullableBigDecimal(ps, 6, t.getNbboAsk());                        // nbbo_ask
        setNullableBigDecimal(ps, 7, t.getUnderlyingPrice());                // underlying_price
        setNullableString(ps, 8, t.getExchange());                           // exchange
        ps.setTimestamp(9, Timestamp.from(t.getExecutedAt()));               // executed_at
        ps.setString(10, t.getOptionChain());                                // option_chain
        setNullableString(ps, 11, t.getCondition());                         // condition
        ps.setBoolean(12, Boolean.TRUE.equals(t.getIsMultileg()));           // is_multileg
        ps.setBoolean(13, Boolean.TRUE.equals(t.getIsFloorTrade()));         // is_floor_trade
        ps.setBoolean(14, Boolean.TRUE.equals(t.getIsSweep()));              // is_sweep
        setNullableString(ps, 15, t.getAggressor());                         // aggressor
        setNullableBigDecimal(ps, 16, t.getPremium());                       // premium
        setNullableString(ps, 17, t.getSide());                              // side
        ps.setTimestamp(18, Timestamp.from(                                  // created_at
            t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now()));
        setNullableDate(ps, 19, t.getExpiry());                              // expiry
        setNullableString(ps, 20, t.getOptionType());                        // option_type
        setNullableInteger(ps, 21, t.getOpenInterest());                     // open_interest
        setNullableBigDecimal(ps, 22, t.getStrike());                        // strike
        setNullableLong(ps, 23, t.getVolume());                              // volume
        setNullableBigDecimal(ps, 24, t.getEwmaNbboAsk());                   // ewma_nbbo_ask
        setNullableBigDecimal(ps, 25, t.getEwmaNbboBid());                   // ewma_nbbo_bid
        setNullableBigDecimal(ps, 26, t.getImpliedVolatility());             // implied_volatility
        setNullableBigDecimal(ps, 27, t.getDelta());                         // delta
        setNullableBigDecimal(ps, 28, t.getTheta());                         // theta
        setNullableBigDecimal(ps, 29, t.getGamma());                         // gamma
        setNullableBigDecimal(ps, 30, t.getVega());                          // vega
        setNullableBigDecimal(ps, 31, t.getRho());                           // rho
        setNullableBigDecimal(ps, 32, t.getTheo());                          // theo
        setNullableLong(ps, 33, t.getAskVol());                              // ask_vol
        setNullableLong(ps, 34, t.getBidVol());                              // bid_vol
        setNullableLong(ps, 35, t.getNoSideVol());                           // no_side_vol
        setNullableLong(ps, 36, t.getMidVol());                              // mid_vol
        setNullableLong(ps, 37, t.getMultiVol());                            // multi_vol
        setNullableLong(ps, 38, t.getStockMultiVol());                       // stock_multi_vol
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int idx, java.math.BigDecimal val) throws SQLException {
        if (val != null) {
            ps.setBigDecimal(idx, val);
        } else {
            ps.setNull(idx, Types.NUMERIC);
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val != null) {
            ps.setString(idx, val);
        } else {
            ps.setNull(idx, Types.VARCHAR);
        }
    }

    private static void setNullableDate(PreparedStatement ps, int idx, java.time.LocalDate val) throws SQLException {
        if (val != null) {
            ps.setDate(idx, Date.valueOf(val));
        } else {
            ps.setNull(idx, Types.DATE);
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null) {
            ps.setInt(idx, val);
        } else {
            ps.setNull(idx, Types.INTEGER);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long val) throws SQLException {
        if (val != null) {
            ps.setLong(idx, val);
        } else {
            ps.setNull(idx, Types.BIGINT);
        }
    }

    private static int countInserted(int[][] results) {
        int count = 0;
        for (int[] batch : results) {
            for (int result : batch) {
                if (result > 0) count++;
            }
        }
        return count;
    }
}
