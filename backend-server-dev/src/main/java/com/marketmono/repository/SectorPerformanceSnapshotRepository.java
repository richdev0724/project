package com.marketmono.repository;

import com.marketmono.entity.SectorPerformanceSnapshot;
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
public interface SectorPerformanceSnapshotRepository extends JpaRepository<SectorPerformanceSnapshot, Long> {

    List<SectorPerformanceSnapshot> findByTickerIn(Collection<String> tickers);

    boolean existsByFetchedAtAfter(Instant cutoff);

    /**
     * Replace-on-write: each collection cycle overwrites the previous snapshot
     * for the same ticker.
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO sector_performance_snapshots (
                ticker, last_price, prev_close, daily_pct, pct_30d, pct_6m,
                volume, last_date, baseline_30d_close, baseline_6m_close, baseline_date,
                fetched_at, created_at, updated_at
            ) VALUES (
                :ticker, :lastPrice, :prevClose, :dailyPct, :pct30d, :pct6m,
                :volume, :lastDate, :baseline30dClose, :baseline6mClose, :baselineDate,
                :fetchedAt, NOW(), NOW()
            )
            ON CONFLICT (ticker) DO UPDATE SET
                last_price         = EXCLUDED.last_price,
                prev_close         = EXCLUDED.prev_close,
                daily_pct          = EXCLUDED.daily_pct,
                pct_30d            = EXCLUDED.pct_30d,
                pct_6m             = EXCLUDED.pct_6m,
                volume             = EXCLUDED.volume,
                last_date          = EXCLUDED.last_date,
                baseline_30d_close = EXCLUDED.baseline_30d_close,
                baseline_6m_close  = EXCLUDED.baseline_6m_close,
                baseline_date      = EXCLUDED.baseline_date,
                fetched_at         = EXCLUDED.fetched_at,
                updated_at         = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("ticker") String ticker,
        @Param("lastPrice") BigDecimal lastPrice,
        @Param("prevClose") BigDecimal prevClose,
        @Param("dailyPct") Double dailyPct,
        @Param("pct30d") Double pct30d,
        @Param("pct6m") Double pct6m,
        @Param("volume") Long volume,
        @Param("lastDate") LocalDate lastDate,
        @Param("baseline30dClose") BigDecimal baseline30dClose,
        @Param("baseline6mClose") BigDecimal baseline6mClose,
        @Param("baselineDate") LocalDate baselineDate,
        @Param("fetchedAt") Instant fetchedAt
    );
}
