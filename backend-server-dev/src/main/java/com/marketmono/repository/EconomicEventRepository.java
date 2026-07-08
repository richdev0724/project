package com.marketmono.repository;

import com.marketmono.entity.EconomicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for economic events data
 */
@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEvent, Long> {
    
    /**
     * Find events within a time range
     */
    List<EconomicEvent> findByEventTimeBetweenOrderByEventTimeAsc(Instant start, Instant end);

    List<EconomicEvent> findByCacheDateBetweenOrderByEventTimeAsc(LocalDate startDate, LocalDate endDate);

    List<EconomicEvent> findByCacheDateBetweenAndRetiredAtIsNullOrderByEventTimeAsc(LocalDate startDate, LocalDate endDate);

    List<EconomicEvent> findByRetiredAtIsNullOrderByEventTimeAsc();

    List<EconomicEvent> findByCacheDateGreaterThanEqualAndRetiredAtIsNullOrderByEventTimeAsc(LocalDate startDate);

    List<EconomicEvent> findByCacheDateLessThanEqualAndRetiredAtIsNullOrderByEventTimeAsc(LocalDate endDate);

    default List<EconomicEvent> findActiveByOptionalCacheDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return findByCacheDateBetweenAndRetiredAtIsNullOrderByEventTimeAsc(startDate, endDate);
        }
        if (startDate != null) {
            return findByCacheDateGreaterThanEqualAndRetiredAtIsNullOrderByEventTimeAsc(startDate);
        }
        if (endDate != null) {
            return findByCacheDateLessThanEqualAndRetiredAtIsNullOrderByEventTimeAsc(endDate);
        }
        return findByRetiredAtIsNullOrderByEventTimeAsc();
    }
    
    /**
     * Find events by cache date
     */
    List<EconomicEvent> findByCacheDate(LocalDate cacheDate);
    
    /**
     * Delete events older than a specific date (for cache cleanup)
     */
    void deleteByCacheDateBefore(LocalDate date);
}
