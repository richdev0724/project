package com.marketmono.repository;

import com.marketmono.entity.SectorEtfPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SectorEtfPriceRepository extends JpaRepository<SectorEtfPrice, Long> {
    
    /**
     * Find price for a specific ticker on a specific date
     */
    Optional<SectorEtfPrice> findByTickerAndDate(String ticker, LocalDate date);
    
    /**
     * Find all prices for a ticker within a date range (inclusive)
     */
    List<SectorEtfPrice> findByTickerAndDateBetweenOrderByDateDesc(
        String ticker, 
        LocalDate startDate, 
        LocalDate endDate
    );
    
    /**
     * Find most recent price for a ticker before or on a specific date
     */
    @Query("""
        SELECT p FROM SectorEtfPrice p
        WHERE p.ticker = :ticker
        AND p.date <= :date
        ORDER BY p.date DESC
        LIMIT 1
        """)
    Optional<SectorEtfPrice> findLatestPriceBeforeDate(
        @Param("ticker") String ticker,
        @Param("date") LocalDate date
    );
    
    /**
     * Find most recent prices for all provided tickers
     */
    @Query("""
        SELECT p FROM SectorEtfPrice p
        WHERE p.ticker IN :tickers
        AND p.date = (
            SELECT MAX(p2.date)
            FROM SectorEtfPrice p2
            WHERE p2.ticker = p.ticker
        )
        """)
    List<SectorEtfPrice> findLatestPricesForTickers(@Param("tickers") List<String> tickers);
    
    /**
     * Find latest price BEFORE specific date for MULTIPLE tickers (N+1 fix)
     * Correlated subquery ensures 1 query vs N queries
     */
    @Query("""
        SELECT p FROM SectorEtfPrice p
        WHERE p.ticker IN :tickers
        AND p.date = (
            SELECT MAX(p2.date)
            FROM SectorEtfPrice p2
            WHERE p2.ticker = p.ticker
            AND p2.date <= :maxDate
        )
        """)
    List<SectorEtfPrice> findLatestPricesBeforeDateBatch(
        @Param("tickers") List<String> tickers,
        @Param("maxDate") LocalDate maxDate
    );
    
    /**
     * Check if price exists for ticker and date (for duplicate prevention)
     */
    boolean existsByTickerAndDate(String ticker, LocalDate date);
}
