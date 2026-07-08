package com.marketmono.repository;

import com.marketmono.entity.SpotGexExposure;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SpotGexExposureRepositoryImpl implements SpotGexExposureRepositoryCustom {

    private static final int HISTORY_QUERY_TIMEOUT_SECONDS = 8;
    private static final int SNAPSHOT_QUERY_TIMEOUT_SECONDS = 8;
    private static final int COLLECTION_STATUS_QUERY_TIMEOUT_SECONDS = 5;

    private static final String FIND_SNAPSHOT_SQL = """
        SELECT
            ticker,
            expiry,
            strike,
            collected_at,
            price,
            date AS market_date,
            source_time,
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
            call_delta_bid,
            call_delta_ask,
            call_gamma_bid,
            call_gamma_ask,
            call_vanna_bid,
            call_vanna_ask,
            call_charm_bid,
            call_charm_ask,
            put_delta_bid,
            put_delta_ask,
            put_gamma_bid,
            put_gamma_ask,
            put_vanna_bid,
            put_vanna_ask,
            put_charm_bid,
            put_charm_ask
        FROM spot_gex_exposures
        WHERE ticker = ?
          AND collected_at = ?
          %s
        ORDER BY expiry ASC, strike ASC
        """;

    private static final String FIND_HISTORY_SQL = """
        WITH history_params AS (
            SELECT CAST(? AS interval) AS bucket_interval
        ),
        candidate_keys AS (
            SELECT DISTINCT ON (
                date_bin(p.bucket_interval, s.collected_at, TIMESTAMPTZ '1970-01-01 00:00:00+00'),
                s.ticker,
                s.expiry,
                s.strike
            )
                date_bin(p.bucket_interval, s.collected_at, TIMESTAMPTZ '1970-01-01 00:00:00+00') AS snapshot_at,
                s.ticker,
                s.expiry,
                s.strike,
                s.collected_at
            FROM spot_gex_exposures s
            CROSS JOIN history_params p
            WHERE s.ticker = ?
              AND s.collected_at >= ?
              %s
            ORDER BY
                date_bin(p.bucket_interval, s.collected_at, TIMESTAMPTZ '1970-01-01 00:00:00+00') ASC,
                s.ticker ASC,
                s.expiry ASC,
                s.strike ASC,
                s.collected_at DESC
        )
        SELECT
            ck.snapshot_at,
            s.ticker,
            s.expiry,
            s.strike,
            s.price,
            s.date AS market_date,
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
            s.call_delta_bid,
            s.call_delta_ask,
            s.call_gamma_bid,
            s.call_gamma_ask,
            s.call_vanna_bid,
            s.call_vanna_ask,
            s.call_charm_bid,
            s.call_charm_ask,
            s.put_delta_bid,
            s.put_delta_ask,
            s.put_gamma_bid,
            s.put_gamma_ask,
            s.put_vanna_bid,
            s.put_vanna_ask,
            s.put_charm_bid,
            s.put_charm_ask
        FROM candidate_keys ck
        JOIN spot_gex_exposures s
          ON s.ticker = ck.ticker
         AND s.expiry = ck.expiry
         AND s.strike = ck.strike
         AND s.collected_at = ck.collected_at
        ORDER BY ck.snapshot_at DESC, s.expiry ASC, s.strike ASC
        """;

    private static final String FIND_COLLECTION_STATUS_SQL = """
        WITH requested_tickers AS (
            SELECT DISTINCT UPPER(TRIM(ticker)) AS ticker
            FROM unnest(?::text[]) AS requested(ticker)
            WHERE TRIM(ticker) <> ''
        )
        SELECT
            rt.ticker,
            latest.last_collected_at,
            COALESCE(today.rows_today, 0) AS rows_today,
            COALESCE(today.collection_cycles, 0) AS collection_cycles
        FROM requested_tickers rt
        LEFT JOIN LATERAL (
            SELECT e.collected_at AS last_collected_at
            FROM spot_gex_exposures e
            WHERE e.ticker = rt.ticker
            ORDER BY e.collected_at DESC
            LIMIT 1
        ) latest ON true
        LEFT JOIN LATERAL (
            SELECT
                COUNT(*) AS rows_today,
                COUNT(DISTINCT e.collected_at) AS collection_cycles
            FROM spot_gex_exposures e
            WHERE e.ticker = rt.ticker
              AND e.collected_at >= CURRENT_DATE
        ) today ON true
        ORDER BY rt.ticker
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SpotGexExposure> findSnapshot(
        String ticker,
        Instant collectedAt,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    ) {
        List<Object> params = new ArrayList<>();
        params.add(ticker);
        params.add(Timestamp.from(collectedAt));

        StringBuilder filters = new StringBuilder();
        if (expiry != null) {
            filters.append("\n          AND expiry = ?");
            params.add(expiry);
        }
        if (minStrike != null) {
            filters.append("\n          AND strike >= ?");
            params.add(minStrike);
        }
        if (maxStrike != null) {
            filters.append("\n          AND strike <= ?");
            params.add(maxStrike);
        }

        String sql = FIND_SNAPSHOT_SQL.formatted(filters);
        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setQueryTimeout(SNAPSHOT_QUERY_TIMEOUT_SECONDS);
                bindParameters(statement, params);
                return statement;
            },
            (rs, rowNum) -> SpotGexExposure.builder()
                .ticker(rs.getString("ticker"))
                .expiry(rs.getObject("expiry", LocalDate.class))
                .strike(rs.getBigDecimal("strike"))
                .collectedAt(toInstant(rs.getTimestamp("collected_at")))
                .price(rs.getBigDecimal("price"))
                .marketDate(rs.getObject("market_date", LocalDate.class))
                .sourceTime(toInstant(rs.getTimestamp("source_time")))
                .callDeltaOi(rs.getBigDecimal("call_delta_oi"))
                .callGammaOi(rs.getBigDecimal("call_gamma_oi"))
                .callVannaOi(rs.getBigDecimal("call_vanna_oi"))
                .callCharmOi(rs.getBigDecimal("call_charm_oi"))
                .putDeltaOi(rs.getBigDecimal("put_delta_oi"))
                .putGammaOi(rs.getBigDecimal("put_gamma_oi"))
                .putVannaOi(rs.getBigDecimal("put_vanna_oi"))
                .putCharmOi(rs.getBigDecimal("put_charm_oi"))
                .callDeltaVol(rs.getBigDecimal("call_delta_vol"))
                .callGammaVol(rs.getBigDecimal("call_gamma_vol"))
                .callVannaVol(rs.getBigDecimal("call_vanna_vol"))
                .callCharmVol(rs.getBigDecimal("call_charm_vol"))
                .putDeltaVol(rs.getBigDecimal("put_delta_vol"))
                .putGammaVol(rs.getBigDecimal("put_gamma_vol"))
                .putVannaVol(rs.getBigDecimal("put_vanna_vol"))
                .putCharmVol(rs.getBigDecimal("put_charm_vol"))
                .callDeltaBid(rs.getBigDecimal("call_delta_bid"))
                .callDeltaAsk(rs.getBigDecimal("call_delta_ask"))
                .callGammaBid(rs.getBigDecimal("call_gamma_bid"))
                .callGammaAsk(rs.getBigDecimal("call_gamma_ask"))
                .callVannaBid(rs.getBigDecimal("call_vanna_bid"))
                .callVannaAsk(rs.getBigDecimal("call_vanna_ask"))
                .callCharmBid(rs.getBigDecimal("call_charm_bid"))
                .callCharmAsk(rs.getBigDecimal("call_charm_ask"))
                .putDeltaBid(rs.getBigDecimal("put_delta_bid"))
                .putDeltaAsk(rs.getBigDecimal("put_delta_ask"))
                .putGammaBid(rs.getBigDecimal("put_gamma_bid"))
                .putGammaAsk(rs.getBigDecimal("put_gamma_ask"))
                .putVannaBid(rs.getBigDecimal("put_vanna_bid"))
                .putVannaAsk(rs.getBigDecimal("put_vanna_ask"))
                .putCharmBid(rs.getBigDecimal("put_charm_bid"))
                .putCharmAsk(rs.getBigDecimal("put_charm_ask"))
                .isNewEntity(false)
                .build()
        );
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
            filters.append("\n              AND s.strike = ?");
            params.add(strike);
        }
        if (minStrike != null) {
            filters.append("\n              AND s.strike >= ?");
            params.add(minStrike);
        }
        if (maxStrike != null) {
            filters.append("\n              AND s.strike <= ?");
            params.add(maxStrike);
        }

        String sql = FIND_HISTORY_SQL.formatted(filters);
        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setQueryTimeout(HISTORY_QUERY_TIMEOUT_SECONDS);
                bindParameters(statement, params);
                return statement;
            },
            (rs, rowNum) -> new SpotGexHistoryProjectionRecord(
                toInstant(rs.getTimestamp("snapshot_at")),
                rs.getString("ticker"),
                rs.getObject("expiry", LocalDate.class),
                rs.getBigDecimal("strike"),
                rs.getBigDecimal("price"),
                rs.getObject("market_date", LocalDate.class),
                toInstant(rs.getTimestamp("source_time")),
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
                rs.getBigDecimal("call_delta_bid"),
                rs.getBigDecimal("call_delta_ask"),
                rs.getBigDecimal("call_gamma_bid"),
                rs.getBigDecimal("call_gamma_ask"),
                rs.getBigDecimal("call_vanna_bid"),
                rs.getBigDecimal("call_vanna_ask"),
                rs.getBigDecimal("call_charm_bid"),
                rs.getBigDecimal("call_charm_ask"),
                rs.getBigDecimal("put_delta_bid"),
                rs.getBigDecimal("put_delta_ask"),
                rs.getBigDecimal("put_gamma_bid"),
                rs.getBigDecimal("put_gamma_ask"),
                rs.getBigDecimal("put_vanna_bid"),
                rs.getBigDecimal("put_vanna_ask"),
                rs.getBigDecimal("put_charm_bid"),
                rs.getBigDecimal("put_charm_ask")
            )
        );
    }

    @Override
    public List<SpotGexExposureRepository.SpotGexStatusProjection> findCollectionStatus(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return List.of();
        }

        return jdbcTemplate.query(
            connection -> {
                PreparedStatement statement = connection.prepareStatement(FIND_COLLECTION_STATUS_SQL);
                statement.setQueryTimeout(COLLECTION_STATUS_QUERY_TIMEOUT_SECONDS);
                statement.setArray(1, connection.createArrayOf("text", tickers.toArray(String[]::new)));
                return statement;
            },
            (rs, rowNum) -> new SpotGexStatusProjectionRecord(
                rs.getString("ticker"),
                toInstant(rs.getTimestamp("last_collected_at")),
                rs.getLong("rows_today"),
                rs.getLong("collection_cycles")
            )
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private void bindParameters(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            statement.setObject(index + 1, params.get(index));
        }
    }

    private record SpotGexStatusProjectionRecord(
        String ticker,
        Instant lastCollectedAt,
        Long rowsToday,
        Long collectionCycles
    ) implements SpotGexExposureRepository.SpotGexStatusProjection {

        @Override
        public String getTicker() {
            return ticker;
        }

        @Override
        public Instant getLastCollectedAt() {
            return lastCollectedAt;
        }

        @Override
        public Long getRowsToday() {
            return rowsToday;
        }

        @Override
        public Long getCollectionCycles() {
            return collectionCycles;
        }
    }

    private record SpotGexHistoryProjectionRecord(
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
