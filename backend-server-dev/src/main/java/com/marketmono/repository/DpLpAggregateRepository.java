package com.marketmono.repository;

import com.marketmono.entity.BucketSize;
import com.marketmono.entity.DpLpAggregate;
import com.marketmono.entity.VenueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DpLpAggregateRepository extends JpaRepository<DpLpAggregate, Long> {

    /**
     * Find aggregate by unique constraint fields.
     */
    Optional<DpLpAggregate> findByTickerAndVenueTypeAndBucketStartAndBucketSize(
        String ticker,
        VenueType venueType,
        OffsetDateTime bucketStart,
        BucketSize bucketSize
    );

    /**
     * Find aggregates for a ticker, venue type, and bucket size within a time range.
     * Used for series data in charts.
     */
    List<DpLpAggregate> findByTickerAndVenueTypeAndBucketSizeAndBucketStartBetweenOrderByBucketStartAsc(
        String ticker,
        VenueType venueType,
        BucketSize bucketSize,
        OffsetDateTime startTime,
        OffsetDateTime endTime
    );

    /**
     * Find aggregates for a ticker and bucket size (both venues) within a time range.
     */
    List<DpLpAggregate> findByTickerAndBucketSizeAndBucketStartBetweenOrderByBucketStartAsc(
        String ticker,
        BucketSize bucketSize,
        OffsetDateTime startTime,
        OffsetDateTime endTime
    );

    /**
     * Get top N tickers by total volume for a specific venue type and time range.
     */
    @Query("SELECT a.ticker, SUM(a.totalVolume) as totalVol " +
           "FROM DpLpAggregate a " +
           "WHERE a.venueType = :venueType " +
           "AND a.bucketSize = :bucketSize " +
           "AND a.bucketStart BETWEEN :startTime AND :endTime " +
           "GROUP BY a.ticker " +
           "ORDER BY totalVol DESC " +
           "LIMIT :limit")
    List<Object[]> findTopTickersByVolume(
        @Param("venueType") VenueType venueType,
        @Param("bucketSize") BucketSize bucketSize,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime,
        @Param("limit") int limit
    );

    /**
     * Get summary statistics for a ticker across all buckets within a time range.
     */
    @Query("SELECT a.venueType, " +
           "CAST(SUM(a.tradeCount) AS long), " +
           "SUM(a.totalVolume), " +
           "AVG(CAST(a.vwap AS double)), " +
           "SUM(a.netImbalance) " +
           "FROM DpLpAggregate a " +
           "WHERE a.ticker = :ticker " +
           "AND a.bucketSize = :bucketSize " +
           "AND a.bucketStart BETWEEN :startTime AND :endTime " +
           "GROUP BY a.venueType")
    List<Object[]> getSummaryStatsByTicker(
        @Param("ticker") String ticker,
        @Param("bucketSize") BucketSize bucketSize,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime
    );

    /**
     * Delete old aggregates before a specific timestamp (for data retention management).
     */
    void deleteByBucketStartBefore(OffsetDateTime timestamp);
}
