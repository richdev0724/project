package com.marketmono.repository;

import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.FlowAlertId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowAlertRepository extends JpaRepository<FlowAlert, FlowAlertId>, JpaSpecificationExecutor<FlowAlert> {
    
    Optional<FlowAlert> findByAlertId(UUID alertId);

    List<FlowAlert> findByAlertIdIn(List<UUID> alertIds);

    Page<FlowAlert> findAllByOrderByTradeStartTimeDesc(Pageable pageable);
    
    Page<FlowAlert> findByTickerInOrderByTradeStartTimeDesc(Iterable<String> tickers, Pageable pageable);
    
    Page<FlowAlert> findByTickerNotInOrderByTradeStartTimeDesc(Iterable<String> tickers, Pageable pageable);

    @Query("SELECT COUNT(f) FROM FlowAlert f WHERE f.createdAt >= :since")
    long countRecentAlerts(@Param("since") Instant since);
    
    /**
     * Find distinct tickers from flow alerts after a specific date
     */
    @Query("SELECT DISTINCT f.ticker FROM FlowAlert f WHERE f.createdAt >= :afterDate ORDER BY f.ticker")
    java.util.List<String> findDistinctTickersAfterDate(@Param("afterDate") Instant afterDate);
}
