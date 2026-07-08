package com.marketmono.repository;

import com.marketmono.entity.ApiRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for ApiRateLimit entity
 * Manages rate limit tracking and queries
 */
@Repository
public interface ApiRateLimitRepository extends JpaRepository<ApiRateLimit, Long> {
    
    /**
     * Find active rate limit record for API key within current window
     * 
     * @param apiKeyHash Hashed API key
     * @param now Current timestamp
     * @return Optional rate limit record
     */
    @Query("""
        SELECT r FROM ApiRateLimit r
        WHERE r.apiKeyHash = :apiKeyHash
        AND r.endpoint IS NULL
        AND r.windowStart <= :now
        AND r.windowEnd >= :now
        ORDER BY r.windowStart DESC
        LIMIT 1
    """)
    Optional<ApiRateLimit> findActiveRateLimit(
        @Param("apiKeyHash") String apiKeyHash,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find active rate limit record for specific endpoint
     * 
     * @param apiKeyHash Hashed API key
     * @param endpoint Specific endpoint
     * @param now Current timestamp
     * @return Optional rate limit record
     */
    @Query("""
        SELECT r FROM ApiRateLimit r
        WHERE r.apiKeyHash = :apiKeyHash
        AND r.endpoint = :endpoint
        AND r.windowStart <= :now
        AND r.windowEnd >= :now
        ORDER BY r.windowStart DESC
        LIMIT 1
    """)
    Optional<ApiRateLimit> findActiveRateLimitForEndpoint(
        @Param("apiKeyHash") String apiKeyHash,
        @Param("endpoint") String endpoint,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Delete expired rate limit records (cleanup)
     * 
     * @param before Timestamp before which records are considered expired
     */
    void deleteByWindowEndBefore(LocalDateTime before);
}
