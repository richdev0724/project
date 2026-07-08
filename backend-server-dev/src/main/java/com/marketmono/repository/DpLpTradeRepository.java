package com.marketmono.repository;

import com.marketmono.entity.DpLpTrade;
import com.marketmono.entity.DpLpTradeId;
import com.marketmono.entity.VenueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface DpLpTradeRepository extends JpaRepository<DpLpTrade, DpLpTradeId> {

    /**
     * Find trades for a specific ticker within a time range.
     */
    List<DpLpTrade> findByTickerAndTsBetweenOrderByTsAsc(
        String ticker, 
        OffsetDateTime startTime, 
        OffsetDateTime endTime
    );

    /**
     * Find trades by ticker, venue type, and time range.
     */
    List<DpLpTrade> findByTickerAndVenueTypeAndTsBetweenOrderByTsAsc(
        String ticker, 
        VenueType venueType, 
        OffsetDateTime startTime, 
        OffsetDateTime endTime
    );

    /**
     * Find trades by venue type and time range (cross-ticker analysis).
     */
    List<DpLpTrade> findByVenueTypeAndTsBetweenOrderByTsAsc(
        VenueType venueType, 
        OffsetDateTime startTime, 
        OffsetDateTime endTime
    );

    /**
     * Find trades by venue type in a half-open time range.
     */
    List<DpLpTrade> findByVenueTypeAndIngestSourceAndTsGreaterThanEqualAndTsLessThanOrderByTsAsc(
        VenueType venueType,
        String ingestSource,
        OffsetDateTime startTime,
        OffsetDateTime endTime
    );

    /**
     * Top dark-pool price levels for a ticker by total premium (price * size),
     * grouped by display price (2 decimals). DB-only — no upstream API calls.
     */
    @Query(value = """
        SELECT ROUND(t.price, 2) AS price,
               SUM(t.size)       AS total_size,
               SUM(t.price * t.size) AS total_premium,
               COUNT(*)          AS trades
        FROM dp_lp_trades t
        WHERE t.ticker = :ticker
          AND t.venue_type = 'DARK'
          AND t.ts >= :startTime
          AND t.ts < :endTime
        GROUP BY ROUND(t.price, 2)
        ORDER BY total_premium DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopDarkPoolLevelsByPremium(
        @Param("ticker") String ticker,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime,
        @Param("limit") int limit
    );

    /**
     * Count trades for a ticker within a time range.
     */
    long countByTickerAndTsBetween(
        String ticker, 
        OffsetDateTime startTime, 
        OffsetDateTime endTime
    );

    /**
     * Get total volume for a ticker and venue type within a time range.
     */
    @Query("SELECT SUM(t.size) FROM DpLpTrade t " +
           "WHERE t.ticker = :ticker " +
           "AND t.venueType = :venueType " +
           "AND t.ts BETWEEN :startTime AND :endTime")
    Long sumVolumeByTickerAndVenueTypeAndTsBetween(
        @Param("ticker") String ticker,
        @Param("venueType") VenueType venueType,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );
}
