package com.marketmono.repository;

import com.marketmono.entity.PoliticianTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Repository for PoliticianTrade entity
 * Provides CRUD operations and custom queries matching upstream market data API filters
 */
@Repository
public interface PoliticianTradeRepository extends JpaRepository<PoliticianTrade, Long> {
    
    /**
     * Find trades by politician ID
     */
    Page<PoliticianTrade> findByPoliticianId(UUID politicianId, Pageable pageable);
    
    /**
     * Find trades by ticker symbol
     */
    Page<PoliticianTrade> findByTicker(String ticker, Pageable pageable);
    
    /**
     * Find trades by transaction date
     */
    Page<PoliticianTrade> findByTransactionDate(LocalDate transactionDate, Pageable pageable);
    
    /**
     * Check if a trade with the given unique key already exists
     * Used by sync service to avoid duplicate imports
     */
    boolean existsByUniqueKey(String uniqueKey);
    
    /**
     * Delete trades older than the given date
     * Used by cleanup service to remove stale data
     * 
     * @param cutoffDate the date before which trades should be deleted
     * @return the number of deleted records
     */
    @Modifying
    @Query("DELETE FROM PoliticianTrade t WHERE t.syncedAt < :cutoffDate")
    int deleteOldTrades(@Param("cutoffDate") LocalDateTime cutoffDate);
}
