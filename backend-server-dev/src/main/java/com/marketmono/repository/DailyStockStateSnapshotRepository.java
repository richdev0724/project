package com.marketmono.repository;

import com.marketmono.entity.DailyStockStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface DailyStockStateSnapshotRepository extends JpaRepository<DailyStockStateSnapshot, Long> {

    /** Most recent snapshot per ticker (one row each). */
    @Query(
        value = """
            SELECT DISTINCT ON (ticker) *
            FROM daily_stock_state_snapshots
            WHERE ticker IN (:tickers)
            ORDER BY ticker, snapshot_date DESC
            """,
        nativeQuery = true
    )
    List<DailyStockStateSnapshot> findLatestByTickerIn(@Param("tickers") Collection<String> tickers);

    boolean existsByFetchedAtAfter(Instant cutoff);

    @Transactional
    @Modifying
    long deleteBySnapshotDateBefore(LocalDate cutoff);

    /** Replace-on-write: re-running a day's collection overwrites that day's row. */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO daily_stock_state_snapshots (
                ticker, snapshot_date, close, prev_close, open, high, low,
                volume, total_volume, tape_time, market_time,
                fetched_at, created_at, updated_at
            ) VALUES (
                :ticker, :snapshotDate, :close, :prevClose, :open, :high, :low,
                :volume, :totalVolume, :tapeTime, :marketTime,
                :fetchedAt, NOW(), NOW()
            )
            ON CONFLICT (ticker, snapshot_date) DO UPDATE SET
                close        = EXCLUDED.close,
                prev_close   = EXCLUDED.prev_close,
                open         = EXCLUDED.open,
                high         = EXCLUDED.high,
                low          = EXCLUDED.low,
                volume       = EXCLUDED.volume,
                total_volume = EXCLUDED.total_volume,
                tape_time    = EXCLUDED.tape_time,
                market_time  = EXCLUDED.market_time,
                fetched_at   = EXCLUDED.fetched_at,
                updated_at   = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("ticker") String ticker,
        @Param("snapshotDate") LocalDate snapshotDate,
        @Param("close") BigDecimal close,
        @Param("prevClose") BigDecimal prevClose,
        @Param("open") BigDecimal open,
        @Param("high") BigDecimal high,
        @Param("low") BigDecimal low,
        @Param("volume") Long volume,
        @Param("totalVolume") Long totalVolume,
        @Param("tapeTime") String tapeTime,
        @Param("marketTime") String marketTime,
        @Param("fetchedAt") Instant fetchedAt
    );
}
