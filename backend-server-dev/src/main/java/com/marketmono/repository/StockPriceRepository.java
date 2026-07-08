package com.marketmono.repository;

import com.marketmono.entity.StockPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    
    /**
     * Find all distinct tickers for a specific date
     */
    @Query("SELECT DISTINCT sp.ticker FROM StockPrice sp WHERE sp.priceDate = :date")
    List<String> findDistinctTickersByDate(@Param("date") LocalDate date);

    @Query("SELECT MAX(sp.priceDate) FROM StockPrice sp WHERE sp.ticker = :ticker")
    LocalDate findMostRecentPriceDateByTicker(@Param("ticker") String ticker);

    @Query("""
        SELECT DISTINCT sp.priceDate FROM StockPrice sp
        WHERE sp.ticker = :ticker
        ORDER BY sp.priceDate DESC
    """)
    List<LocalDate> findDistinctPriceDatesByTicker(
        @Param("ticker") String ticker,
        Pageable pageable
    );

    @Query("""
        SELECT MAX(sp.priceDate) FROM StockPrice sp
        WHERE sp.ticker = :ticker
        AND sp.priceTime = :summaryTime
    """)
    LocalDate findMostRecentDailySummaryDateByTicker(
        @Param("ticker") String ticker,
        @Param("summaryTime") LocalTime summaryTime
    );

    /**
     * Find all stock prices for a ticker on a specific date, ordered by time
     */
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker = :ticker AND sp.priceDate = :date ORDER BY sp.priceTime")
    List<StockPrice> findByTickerAndDateOrderByTime(
        @Param("ticker") String ticker,
        @Param("date") LocalDate date
    );

    @Query("""
        SELECT sp FROM StockPrice sp
        WHERE sp.ticker = :ticker
        AND sp.priceDate = :date
        AND sp.priceTime <> :summaryTime
        ORDER BY sp.priceTime
    """)
    List<StockPrice> findIntradayPricesByTickerAndDateOrderByTime(
        @Param("ticker") String ticker,
        @Param("date") LocalDate date,
        @Param("summaryTime") LocalTime summaryTime
    );
    
    /**
     * Find stock prices for a ticker between date range
     */
    @Query("""
        SELECT sp FROM StockPrice sp 
        WHERE sp.ticker = :ticker 
        AND sp.priceDate BETWEEN :startDate AND :endDate 
        ORDER BY sp.priceDate, sp.priceTime
    """)
    List<StockPrice> findByTickerAndDateRange(
        @Param("ticker") String ticker,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT sp FROM StockPrice sp
        WHERE sp.ticker = :ticker
        AND sp.priceDate BETWEEN :startDate AND :endDate
        AND sp.priceTime <> :summaryTime
        ORDER BY sp.priceDate, sp.priceTime
    """)
    List<StockPrice> findIntradayPricesByTickerAndDateRange(
        @Param("ticker") String ticker,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("summaryTime") LocalTime summaryTime
    );
    
    /**
     * Find most recent stock price for a ticker
     */
    @Query("""
        SELECT sp FROM StockPrice sp 
        WHERE sp.ticker = :ticker 
        ORDER BY sp.priceDate DESC, sp.priceTime DESC 
        LIMIT 1
    """)
    StockPrice findMostRecentByTicker(@Param("ticker") String ticker);
    
    /**
     * Delete stock prices older than a specific date
     */
    @Modifying
    @Query("DELETE FROM StockPrice sp WHERE sp.priceDate < :cutoffDate")
    int deleteByPriceDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    @Modifying
    @Query("""
        DELETE FROM StockPrice sp
        WHERE sp.priceDate < :cutoffDate
        AND sp.priceTime <> :summaryTime
    """)
    int deleteIntradayPricesBefore(
        @Param("cutoffDate") LocalDate cutoffDate,
        @Param("summaryTime") LocalTime summaryTime
    );

    @Modifying
    @Query("""
        DELETE FROM StockPrice sp
        WHERE sp.priceDate < :cutoffDate
        AND sp.priceTime = :summaryTime
    """)
    int deleteDailySummaryPricesBefore(
        @Param("cutoffDate") LocalDate cutoffDate,
        @Param("summaryTime") LocalTime summaryTime
    );
    
    /**
     * Find daily closing prices for a ticker (where open, high, low are all set)
     */
    @Query("""
        SELECT sp FROM StockPrice sp 
        WHERE sp.ticker = :ticker 
        AND sp.open IS NOT NULL 
        AND sp.high IS NOT NULL 
        AND sp.low IS NOT NULL 
        AND sp.priceDate BETWEEN :startDate AND :endDate 
        ORDER BY sp.priceDate DESC
    """)
    List<StockPrice> findDailyPricesByTickerAndDateRange(
        @Param("ticker") String ticker,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT sp FROM StockPrice sp
        WHERE sp.ticker = :ticker
        AND sp.open IS NOT NULL
        AND sp.high IS NOT NULL
        AND sp.low IS NOT NULL
        AND sp.priceTime = :summaryTime
        AND sp.priceDate BETWEEN :startDate AND :endDate
        ORDER BY sp.priceDate, sp.priceTime
    """)
    List<StockPrice> findDailySummaryPricesByTickerAndDateRange(
        @Param("ticker") String ticker,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("summaryTime") LocalTime summaryTime
    );
    
    /**
     * Check if data exists for a ticker on a specific date
     */
    @Query("SELECT COUNT(sp) > 0 FROM StockPrice sp WHERE sp.ticker = :ticker AND sp.priceDate = :date")
    boolean existsByTickerAndDate(@Param("ticker") String ticker, @Param("date") LocalDate date);
}
