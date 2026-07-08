package com.marketmono.repository;

import com.marketmono.entity.DashboardOhlcSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface DashboardOhlcSnapshotRepository extends JpaRepository<DashboardOhlcSnapshot, Long> {

    List<DashboardOhlcSnapshot> findByTimeframeAndTickerIn(String timeframe, Collection<String> tickers);

    boolean existsByFetchedAtAfter(Instant cutoff);

    boolean existsByTimeframeAndFetchedAtAfter(String timeframe, Instant cutoff);

    /**
     * Replace-on-write: each collection cycle overwrites the previous snapshot
     * for the same (ticker, timeframe).
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO dashboard_ohlc_snapshots (
                ticker, timeframe, previous_session_close, series, fetched_at, created_at, updated_at
            ) VALUES (
                :ticker, :timeframe, :previousSessionClose, CAST(:series AS jsonb), :fetchedAt, NOW(), NOW()
            )
            ON CONFLICT (ticker, timeframe) DO UPDATE SET
                previous_session_close = EXCLUDED.previous_session_close,
                series                 = EXCLUDED.series,
                fetched_at             = EXCLUDED.fetched_at,
                updated_at             = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("ticker") String ticker,
        @Param("timeframe") String timeframe,
        @Param("previousSessionClose") BigDecimal previousSessionClose,
        @Param("series") String series,
        @Param("fetchedAt") Instant fetchedAt
    );

    @Transactional
    @Modifying
    @Query("DELETE FROM DashboardOhlcSnapshot s WHERE s.fetchedAt < :cutoff")
    int deleteByFetchedAtBefore(@Param("cutoff") Instant cutoff);
}
