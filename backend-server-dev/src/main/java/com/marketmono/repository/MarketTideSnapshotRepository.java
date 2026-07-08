package com.marketmono.repository;

import com.marketmono.entity.MarketTideSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MarketTideSnapshotRepository extends JpaRepository<MarketTideSnapshot, Long> {

    Optional<MarketTideSnapshot> findTopByOrderByTradingDateDesc();

    boolean existsByFetchedAtAfter(Instant cutoff);

    /**
     * Replace-on-write: each collection cycle overwrites the current trading
     * day's snapshot.
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO market_tide_snapshots (
                trading_date, series, fetched_at, created_at, updated_at
            ) VALUES (
                :tradingDate, CAST(:series AS jsonb), :fetchedAt, NOW(), NOW()
            )
            ON CONFLICT (trading_date) DO UPDATE SET
                series     = EXCLUDED.series,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("tradingDate") LocalDate tradingDate,
        @Param("series") String series,
        @Param("fetchedAt") Instant fetchedAt
    );

    @Transactional
    @Modifying
    @Query("DELETE FROM MarketTideSnapshot s WHERE s.tradingDate < :cutoff")
    int deleteByTradingDateBefore(@Param("cutoff") LocalDate cutoff);
}
