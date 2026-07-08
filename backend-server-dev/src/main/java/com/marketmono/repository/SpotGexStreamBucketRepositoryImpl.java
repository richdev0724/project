package com.marketmono.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpotGexStreamBucketRepositoryImpl implements SpotGexStreamBucketRepository {

    private static final int QUERY_TIMEOUT_SECONDS = 8;

    private static final String FIND_LATEST_BUCKET_SQL = """
        SELECT MAX(bucket_start)
        FROM spot_gex_strike_expiry_current
        WHERE ticker = ?
        """;

    private static final String FIND_STATUS_SQL = """
        WITH requested AS (
            SELECT DISTINCT upper(trim(value)) AS ticker
            FROM unnest(CAST(? AS text[])) AS value
            WHERE trim(value) <> ''
        )
        SELECT
            r.ticker,
            stats.latest_bucket_start,
            0::bigint AS rows_today,
            0::bigint AS rows_latest_session,
            COALESCE(stats.current_rows, 0) AS current_rows
        FROM requested r
        LEFT JOIN LATERAL (
            SELECT
                MAX(c.bucket_start) AS latest_bucket_start,
                COUNT(*)::bigint AS current_rows
            FROM spot_gex_strike_expiry_current c
            WHERE c.ticker = r.ticker
        ) stats ON true
        ORDER BY r.ticker
        """;

    private static final String FIND_SNAPSHOT_SQL = """
        SELECT
            bucket_start AS snapshot_at,
            ticker,
            expiry,
            strike_price AS strike,
            underlying_price AS price,
            CAST(bucket_start AT TIME ZONE 'UTC' AS DATE) AS market_date,
            to_timestamp(latest_timestamp_ms / 1000.0) AS source_time,
            call_delta_oi,
            call_gamma_oi,
            call_vanna_oi,
            call_charm_oi,
            put_delta_oi,
            put_gamma_oi,
            put_vanna_oi,
            put_charm_oi,
            call_delta_vol,
            call_gamma_vol,
            call_vanna_vol,
            call_charm_vol,
            put_delta_vol,
            put_gamma_vol,
            put_vanna_vol,
            put_charm_vol,
            call_gamma_bid,
            call_gamma_ask,
            call_vanna_bid,
            call_vanna_ask,
            call_charm_bid,
            call_charm_ask,
            put_gamma_bid,
            put_gamma_ask,
            put_vanna_bid,
            put_vanna_ask,
            put_charm_bid,
            put_charm_ask
        FROM spot_gex_strike_expiry_current
        WHERE ticker = ?
          AND bucket_start <= ?
          AND bucket_start >= ?
          %s
        ORDER BY expiry ASC, strike_price DESC
        """;

    /**
     * Single-pass DISTINCT ON: the previous two-step form (candidate-key CTE +
     * self-join on the primary key) forced per-row decompression of the
     * Timescale columnar chunks and ran ~1000x slower than reading the payload
     * columns in the same DISTINCT ON scan.
     */
    private static final String FIND_HISTORY_SQL = """
        SELECT *
        FROM (
            SELECT DISTINCT ON (
                date_bin(p.bucket_interval, s.bucket_start, TIMESTAMPTZ '1970-01-01 00:00:00+00'),
                s.ticker,
                s.expiry,
                s.strike_price_cents
            )
                date_bin(p.bucket_interval, s.bucket_start, TIMESTAMPTZ '1970-01-01 00:00:00+00') AS snapshot_at,
                s.ticker,
                s.expiry,
                s.strike_price AS strike,
                s.underlying_price AS price,
                CAST(s.bucket_start AT TIME ZONE 'UTC' AS DATE) AS market_date,
                to_timestamp(s.latest_timestamp_ms / 1000.0) AS source_time,
                s.call_delta_oi,
                s.call_gamma_oi,
                s.call_vanna_oi,
                s.call_charm_oi,
                s.put_delta_oi,
                s.put_gamma_oi,
                s.put_vanna_oi,
                s.put_charm_oi,
                s.call_delta_vol,
                s.call_gamma_vol,
                s.call_vanna_vol,
                s.call_charm_vol,
                s.put_delta_vol,
                s.put_gamma_vol,
                s.put_vanna_vol,
                s.put_charm_vol,
                s.call_gamma_bid,
                s.call_gamma_ask,
                s.call_vanna_bid,
                s.call_vanna_ask,
                s.call_charm_bid,
                s.call_charm_ask,
                s.put_gamma_bid,
                s.put_gamma_ask,
                s.put_vanna_bid,
                s.put_vanna_ask,
                s.put_charm_bid,
                s.put_charm_ask
            FROM spot_gex_strike_expiry_1m s
            CROSS JOIN (SELECT CAST(? AS interval) AS bucket_interval) p
            WHERE s.ticker = ?
              AND s.bucket_start >= ?
              %s
            ORDER BY
                date_bin(p.bucket_interval, s.bucket_start, TIMESTAMPTZ '1970-01-01 00:00:00+00') ASC,
                s.ticker ASC,
                s.expiry ASC,
                s.strike_price_cents ASC,
                s.bucket_start DESC
        ) history
        ORDER BY snapshot_at DESC, expiry ASC, strike ASC
        """;

    private static final String FIND_REPLAY_SESSION_BUCKETS_SQL = """
        SELECT DISTINCT bucket_start
        FROM spot_gex_strike_expiry_1m
        WHERE ticker = ?
          AND bucket_start >= ?
          AND bucket_start <= ?
        ORDER BY bucket_start ASC
        """;

    private static final String FIND_REPLAY_SNAPSHOT_SQL = """
        WITH candidate_rows AS (
            SELECT DISTINCT ON (s.ticker, s.expiry, s.strike_price_cents)
                CAST(? AS timestamptz) AS snapshot_at,
                s.ticker,
                s.expiry,
                s.strike_price AS strike,
                s.underlying_price AS price,
                CAST(s.bucket_start AT TIME ZONE 'America/New_York' AS DATE) AS market_date,
                to_timestamp(s.latest_timestamp_ms / 1000.0) AS source_time,
                s.call_delta_oi,
                s.call_gamma_oi,
                s.call_vanna_oi,
                s.call_charm_oi,
                s.put_delta_oi,
                s.put_gamma_oi,
                s.put_vanna_oi,
                s.put_charm_oi,
                s.call_delta_vol,
                s.call_gamma_vol,
                s.call_vanna_vol,
                s.call_charm_vol,
                s.put_delta_vol,
                s.put_gamma_vol,
                s.put_vanna_vol,
                s.put_charm_vol,
                s.call_gamma_bid,
                s.call_gamma_ask,
                s.call_vanna_bid,
                s.call_vanna_ask,
                s.call_charm_bid,
                s.call_charm_ask,
                s.put_gamma_bid,
                s.put_gamma_ask,
                s.put_vanna_bid,
                s.put_vanna_ask,
                s.put_charm_bid,
                s.put_charm_ask
            FROM spot_gex_strike_expiry_1m s
            WHERE s.ticker = ?
              AND s.bucket_start <= ?
              AND s.bucket_start >= ?
              %s
            ORDER BY
                s.ticker ASC,
                s.expiry ASC,
                s.strike_price_cents ASC,
                s.bucket_start DESC,
                s.latest_timestamp_ms DESC
        )
        SELECT
            s.snapshot_at,
            s.ticker,
            s.expiry,
            s.strike,
            s.price,
            s.market_date,
            s.source_time,
            s.call_delta_oi,
            s.call_gamma_oi,
            s.call_vanna_oi,
            s.call_charm_oi,
            s.put_delta_oi,
            s.put_gamma_oi,
            s.put_vanna_oi,
            s.put_charm_oi,
            s.call_delta_vol,
            s.call_gamma_vol,
            s.call_vanna_vol,
            s.call_charm_vol,
            s.put_delta_vol,
            s.put_gamma_vol,
            s.put_vanna_vol,
            s.put_charm_vol,
            s.call_gamma_bid,
            s.call_gamma_ask,
            s.call_vanna_bid,
            s.call_vanna_ask,
            s.call_charm_bid,
            s.call_charm_ask,
            s.put_gamma_bid,
            s.put_gamma_ask,
            s.put_vanna_bid,
            s.put_vanna_ask,
            s.put_charm_bid,
            s.put_charm_ask
        FROM candidate_rows s
        ORDER BY s.expiry ASC, s.strike DESC
        """;

    private final JdbcTemplate jdbcTemplate;

    static String findSnapshotSql() {
        return FIND_SNAPSHOT_SQL;
    }

    static String findHistorySql() {
        return FIND_HISTORY_SQL;
    }

    static String findStatusSql() {
        return FIND_STATUS_SQL;
    }

    static String findReplaySessionBucketsSql() {
        return FIND_REPLAY_SESSION_BUCKETS_SQL;
    }

    static String findReplaySnapshotSql() {
        return FIND_REPLAY_SNAPSHOT_SQL;
    }

    @Override
    public Optional<Instant> findLatestBucketStartByTicker(String ticker) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
            FIND_LATEST_BUCKET_SQL,
            Timestamp.class,
            ticker
        );
        return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
    }

    @Override
    public List<StreamBucketStatusProjection> findStatusByTicker(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(FIND_STATUS_SQL);
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                Array tickerArray = connection.createArrayOf("text", tickers.toArray(String[]::new));
                statement.setArray(1, tickerArray);
                return statement;
            },
            (rs, rowNum) -> {
                Timestamp latestBucketStart = rs.getTimestamp("latest_bucket_start");
                return new StreamBucketStatusProjection(
                    rs.getString("ticker"),
                    latestBucketStart != null ? latestBucketStart.toInstant() : null,
                    rs.getLong("rows_today"),
                    rs.getLong("rows_latest_session"),
                    rs.getLong("current_rows")
                );
            }
        );
    }

    @Override
    public List<SpotGexExposureRepository.SpotGexHistoryProjection> findSnapshot(
        String ticker,
        Instant bucketStart,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    ) {
        List<Object> params = new ArrayList<>();
        params.add(ticker);
        params.add(Timestamp.from(bucketStart));
        params.add(Timestamp.from(bucketStart.minus(Duration.ofDays(1))));

        StringBuilder filters = new StringBuilder();
        if (expiry != null) {
            filters.append("\n          AND expiry = ?");
            params.add(expiry);
        }
        if (minStrike != null) {
            filters.append("\n          AND strike_price >= ?");
            params.add(minStrike);
        }
        if (maxStrike != null) {
            filters.append("\n          AND strike_price <= ?");
            params.add(maxStrike);
        }

        return query(FIND_SNAPSHOT_SQL.formatted(filters), params);
    }

    @Override
    public List<Instant> findReplaySessionBucketStarts(
        String ticker,
        Instant openAt,
        Instant closeAt
    ) {
        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(FIND_REPLAY_SESSION_BUCKETS_SQL);
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                bindParameters(statement, List.of(
                    ticker,
                    Timestamp.from(openAt),
                    Timestamp.from(closeAt)
                ));
                return statement;
            },
            (rs, rowNum) -> rs.getTimestamp("bucket_start").toInstant()
        );
    }

    @Override
    public List<SpotGexExposureRepository.SpotGexHistoryProjection> findReplaySnapshot(
        String ticker,
        Instant bucketStart,
        Instant sessionOpenAt,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    ) {
        List<Object> params = new ArrayList<>();
        params.add(Timestamp.from(bucketStart));
        params.add(ticker);
        params.add(Timestamp.from(bucketStart));
        params.add(Timestamp.from(sessionOpenAt));

        StringBuilder filters = new StringBuilder();
        if (expiry != null) {
            filters.append("\n          AND s.expiry = ?");
            params.add(expiry);
        }
        if (minStrike != null) {
            filters.append("\n          AND s.strike_price >= ?");
            params.add(minStrike);
        }
        if (maxStrike != null) {
            filters.append("\n          AND s.strike_price <= ?");
            params.add(maxStrike);
        }

        return query(FIND_REPLAY_SNAPSHOT_SQL.formatted(filters), params);
    }

    @Override
    public List<SpotGexExposureRepository.SpotGexHistoryProjection> findHistory(
        String ticker,
        Instant since,
        String bucketInterval,
        LocalDate expiry,
        BigDecimal strike,
        BigDecimal minStrike,
        BigDecimal maxStrike
    ) {
        List<Object> params = new ArrayList<>();
        params.add(bucketInterval);
        params.add(ticker);
        params.add(Timestamp.from(since));

        StringBuilder filters = new StringBuilder();
        if (expiry != null) {
            filters.append("\n              AND s.expiry = ?");
            params.add(expiry);
        }
        if (strike != null) {
            filters.append("\n              AND s.strike_price = ?");
            params.add(strike);
        }
        if (minStrike != null) {
            filters.append("\n              AND s.strike_price >= ?");
            params.add(minStrike);
        }
        if (maxStrike != null) {
            filters.append("\n              AND s.strike_price <= ?");
            params.add(maxStrike);
        }

        return query(FIND_HISTORY_SQL.formatted(filters), params);
    }

    private List<SpotGexExposureRepository.SpotGexHistoryProjection> query(String sql, List<Object> params) {
        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                bindParameters(statement, params);
                return statement;
            },
            (rs, rowNum) -> new StreamBucketProjection(
                rs.getTimestamp("snapshot_at").toInstant(),
                rs.getString("ticker"),
                rs.getObject("expiry", LocalDate.class),
                rs.getBigDecimal("strike"),
                rs.getBigDecimal("price"),
                rs.getObject("market_date", LocalDate.class),
                rs.getTimestamp("source_time").toInstant(),
                rs.getBigDecimal("call_delta_oi"),
                rs.getBigDecimal("call_gamma_oi"),
                rs.getBigDecimal("call_vanna_oi"),
                rs.getBigDecimal("call_charm_oi"),
                rs.getBigDecimal("put_delta_oi"),
                rs.getBigDecimal("put_gamma_oi"),
                rs.getBigDecimal("put_vanna_oi"),
                rs.getBigDecimal("put_charm_oi"),
                rs.getBigDecimal("call_delta_vol"),
                rs.getBigDecimal("call_gamma_vol"),
                rs.getBigDecimal("call_vanna_vol"),
                rs.getBigDecimal("call_charm_vol"),
                rs.getBigDecimal("put_delta_vol"),
                rs.getBigDecimal("put_gamma_vol"),
                rs.getBigDecimal("put_vanna_vol"),
                rs.getBigDecimal("put_charm_vol"),
                null,
                null,
                rs.getBigDecimal("call_gamma_bid"),
                rs.getBigDecimal("call_gamma_ask"),
                rs.getBigDecimal("call_vanna_bid"),
                rs.getBigDecimal("call_vanna_ask"),
                rs.getBigDecimal("call_charm_bid"),
                rs.getBigDecimal("call_charm_ask"),
                null,
                null,
                rs.getBigDecimal("put_gamma_bid"),
                rs.getBigDecimal("put_gamma_ask"),
                rs.getBigDecimal("put_vanna_bid"),
                rs.getBigDecimal("put_vanna_ask"),
                rs.getBigDecimal("put_charm_bid"),
                rs.getBigDecimal("put_charm_ask")
            )
        );
    }

    private void bindParameters(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            statement.setObject(index + 1, params.get(index));
        }
    }

    private record StreamBucketProjection(
        Instant snapshotAt,
        String ticker,
        LocalDate expiry,
        BigDecimal strike,
        BigDecimal price,
        LocalDate marketDate,
        Instant sourceTime,
        BigDecimal callDeltaOi,
        BigDecimal callGammaOi,
        BigDecimal callVannaOi,
        BigDecimal callCharmOi,
        BigDecimal putDeltaOi,
        BigDecimal putGammaOi,
        BigDecimal putVannaOi,
        BigDecimal putCharmOi,
        BigDecimal callDeltaVol,
        BigDecimal callGammaVol,
        BigDecimal callVannaVol,
        BigDecimal callCharmVol,
        BigDecimal putDeltaVol,
        BigDecimal putGammaVol,
        BigDecimal putVannaVol,
        BigDecimal putCharmVol,
        BigDecimal callDeltaBid,
        BigDecimal callDeltaAsk,
        BigDecimal callGammaBid,
        BigDecimal callGammaAsk,
        BigDecimal callVannaBid,
        BigDecimal callVannaAsk,
        BigDecimal callCharmBid,
        BigDecimal callCharmAsk,
        BigDecimal putDeltaBid,
        BigDecimal putDeltaAsk,
        BigDecimal putGammaBid,
        BigDecimal putGammaAsk,
        BigDecimal putVannaBid,
        BigDecimal putVannaAsk,
        BigDecimal putCharmBid,
        BigDecimal putCharmAsk
    ) implements SpotGexExposureRepository.SpotGexHistoryProjection {
        @Override public Instant getSnapshotAt() { return snapshotAt; }
        @Override public String getTicker() { return ticker; }
        @Override public LocalDate getExpiry() { return expiry; }
        @Override public BigDecimal getStrike() { return strike; }
        @Override public BigDecimal getPrice() { return price; }
        @Override public LocalDate getMarketDate() { return marketDate; }
        @Override public Instant getSourceTime() { return sourceTime; }
        @Override public BigDecimal getCallDeltaOi() { return callDeltaOi; }
        @Override public BigDecimal getCallGammaOi() { return callGammaOi; }
        @Override public BigDecimal getCallVannaOi() { return callVannaOi; }
        @Override public BigDecimal getCallCharmOi() { return callCharmOi; }
        @Override public BigDecimal getPutDeltaOi() { return putDeltaOi; }
        @Override public BigDecimal getPutGammaOi() { return putGammaOi; }
        @Override public BigDecimal getPutVannaOi() { return putVannaOi; }
        @Override public BigDecimal getPutCharmOi() { return putCharmOi; }
        @Override public BigDecimal getCallDeltaVol() { return callDeltaVol; }
        @Override public BigDecimal getCallGammaVol() { return callGammaVol; }
        @Override public BigDecimal getCallVannaVol() { return callVannaVol; }
        @Override public BigDecimal getCallCharmVol() { return callCharmVol; }
        @Override public BigDecimal getPutDeltaVol() { return putDeltaVol; }
        @Override public BigDecimal getPutGammaVol() { return putGammaVol; }
        @Override public BigDecimal getPutVannaVol() { return putVannaVol; }
        @Override public BigDecimal getPutCharmVol() { return putCharmVol; }
        @Override public BigDecimal getCallDeltaBid() { return callDeltaBid; }
        @Override public BigDecimal getCallDeltaAsk() { return callDeltaAsk; }
        @Override public BigDecimal getCallGammaBid() { return callGammaBid; }
        @Override public BigDecimal getCallGammaAsk() { return callGammaAsk; }
        @Override public BigDecimal getCallVannaBid() { return callVannaBid; }
        @Override public BigDecimal getCallVannaAsk() { return callVannaAsk; }
        @Override public BigDecimal getCallCharmBid() { return callCharmBid; }
        @Override public BigDecimal getCallCharmAsk() { return callCharmAsk; }
        @Override public BigDecimal getPutDeltaBid() { return putDeltaBid; }
        @Override public BigDecimal getPutDeltaAsk() { return putDeltaAsk; }
        @Override public BigDecimal getPutGammaBid() { return putGammaBid; }
        @Override public BigDecimal getPutGammaAsk() { return putGammaAsk; }
        @Override public BigDecimal getPutVannaBid() { return putVannaBid; }
        @Override public BigDecimal getPutVannaAsk() { return putVannaAsk; }
        @Override public BigDecimal getPutCharmBid() { return putCharmBid; }
        @Override public BigDecimal getPutCharmAsk() { return putCharmAsk; }
    }
}
