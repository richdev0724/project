package com.marketmono.service;

import com.marketmono.entity.FlowAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowAlertBatchPersistence {

    private static final int JDBC_BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
        INSERT INTO flow_alerts (
            alert_id, ticker, option_symbol, strike, expiry_date, option_type,
            price, bid, ask, underlying_price, total_size, total_premium,
            trade_count, volume, open_interest, volume_oi_ratio,
            implied_volatility_start, implied_volatility_end, alert_rule,
            has_sweep, has_floor, has_singleleg, has_multileg,
            all_opening_trades, sector_id, market_cap, next_earnings_date,
            trade_start_time, trade_end_time, total_ask_side_premium,
            total_bid_side_premium, data_source, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?::option_type_enum, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::data_source_enum, ?, ?)
        ON CONFLICT (alert_id, trade_start_time) DO NOTHING
        """;

    private static final String CLAIM_SQL = """
        INSERT INTO flow_alert_ingest_keys (
            alert_id, first_trade_start_time, source
        ) VALUES (?, ?, ?)
        ON CONFLICT (alert_id) DO NOTHING
        RETURNING alert_id
        """;

    private static final String CLEANUP_INGEST_KEYS_SQL = """
        DELETE FROM flow_alert_ingest_keys
        WHERE created_at < ?
        """;

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public FlowAlertBatchResult persistBatch(List<FlowAlert> alerts) {
        if (alerts.isEmpty()) {
            return FlowAlertBatchResult.empty();
        }

        List<FlowAlert> claimedAlerts = claimUnseenAlerts(alerts);
        if (claimedAlerts.isEmpty()) {
            return new FlowAlertBatchResult(List.of(), alerts.size());
        }

        int[][] results = jdbcTemplate.batchUpdate(
            INSERT_SQL,
            claimedAlerts,
            JDBC_BATCH_SIZE,
            this::setParameters
        );

        List<FlowAlert> inserted = collectInsertedAlerts(claimedAlerts, results);
        return new FlowAlertBatchResult(inserted, alerts.size() - inserted.size());
    }

    private List<FlowAlert> claimUnseenAlerts(List<FlowAlert> alerts) {
        List<FlowAlert> claimedAlerts = new ArrayList<>();
        for (FlowAlert alert : alerts) {
            try {
                java.util.UUID claimed = jdbcTemplate.queryForObject(
                    CLAIM_SQL,
                    java.util.UUID.class,
                    alert.getAlertId(),
                    Timestamp.from(alert.getTradeStartTime()),
                    alert.getDataSource().name()
                );
                if (claimed != null) {
                    claimedAlerts.add(alert);
                }
            } catch (EmptyResultDataAccessException _) {
                // The id already exists in the dedupe ledger.
            }
        }
        return claimedAlerts;
    }

    @Transactional
    public int cleanupIngestKeysBefore(Instant cutoff) {
        return jdbcTemplate.update(CLEANUP_INGEST_KEYS_SQL, Timestamp.from(cutoff));
    }

    private void setParameters(PreparedStatement ps, FlowAlert alert) throws SQLException {
        Instant now = Instant.now();

        ps.setObject(1, alert.getAlertId());
        ps.setString(2, alert.getTicker());
        ps.setString(3, alert.getOptionSymbol());
        ps.setBigDecimal(4, alert.getStrike());
        setNullableDate(ps, 5, alert.getExpiryDate());
        ps.setString(6, alert.getOptionType().name());
        setNullableBigDecimal(ps, 7, alert.getPrice());
        setNullableBigDecimal(ps, 8, alert.getBid());
        setNullableBigDecimal(ps, 9, alert.getAsk());
        setNullableBigDecimal(ps, 10, alert.getUnderlyingPrice());
        setNullableInteger(ps, 11, alert.getTotalSize());
        setNullableBigDecimal(ps, 12, alert.getTotalPremium());
        setNullableInteger(ps, 13, alert.getTradeCount());
        setNullableInteger(ps, 14, alert.getVolume());
        setNullableInteger(ps, 15, alert.getOpenInterest());
        setNullableBigDecimal(ps, 16, alert.getVolumeOiRatio());
        setNullableBigDecimal(ps, 17, alert.getImpliedVolatilityStart());
        setNullableBigDecimal(ps, 18, alert.getImpliedVolatilityEnd());
        setNullableString(ps, 19, alert.getAlertRule() != null ? alert.getAlertRule().name() : null);
        ps.setBoolean(20, Boolean.TRUE.equals(alert.getHasSweep()));
        ps.setBoolean(21, Boolean.TRUE.equals(alert.getHasFloor()));
        ps.setBoolean(22, Boolean.TRUE.equals(alert.getHasSingleleg()));
        ps.setBoolean(23, Boolean.TRUE.equals(alert.getHasMultileg()));
        ps.setBoolean(24, Boolean.TRUE.equals(alert.getAllOpeningTrades()));
        setNullableInteger(ps, 25, alert.getSector() != null ? alert.getSector().getId() : null);
        setNullableLong(ps, 26, alert.getMarketCap());
        setNullableDate(ps, 27, alert.getNextEarningsDate());
        ps.setTimestamp(28, Timestamp.from(alert.getTradeStartTime()));
        setNullableTimestamp(ps, 29, alert.getTradeEndTime());
        setNullableBigDecimal(ps, 30, alert.getTotalAskSidePremium());
        setNullableBigDecimal(ps, 31, alert.getTotalBidSidePremium());
        ps.setString(32, alert.getDataSource().name());
        ps.setTimestamp(33, Timestamp.from(alert.getCreatedAt() != null ? alert.getCreatedAt() : now));
        ps.setTimestamp(34, Timestamp.from(alert.getUpdatedAt() != null ? alert.getUpdatedAt() : now));
    }

    private static List<FlowAlert> collectInsertedAlerts(List<FlowAlert> alerts, int[][] results) {
        List<FlowAlert> inserted = new ArrayList<>();
        int index = 0;

        for (int[] batch : results) {
            for (int result : batch) {
                if (index >= alerts.size()) {
                    return inserted;
                }
                if (result > 0 || result == Statement.SUCCESS_NO_INFO) {
                    inserted.add(alerts.get(index));
                }
                index++;
            }
        }

        return inserted;
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, java.math.BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }

    private static void setNullableDate(PreparedStatement ps, int index, java.time.LocalDate value) throws SQLException {
        if (value != null) {
            ps.setDate(index, Date.valueOf(value));
        } else {
            ps.setNull(index, Types.DATE);
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    public record FlowAlertBatchResult(
        List<FlowAlert> insertedAlerts,
        int duplicateCount
    ) {

        static FlowAlertBatchResult empty() {
            return new FlowAlertBatchResult(List.of(), 0);
        }
    }
}
