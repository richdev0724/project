package com.marketmono.repository;

import com.marketmono.entity.StripeConfigAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for StripeConfigAuditLog entity operations.
 * Uses column names from 015-stripe-config-tables.yaml
 */
@Repository
public interface StripeConfigAuditLogRepository extends JpaRepository<StripeConfigAuditLog, UUID> {
    
    /**
     * Find all audit logs ordered by change date (newest first).
     */
    List<StripeConfigAuditLog> findAllByOrderByChangedAtDesc();
    
    /**
     * Find audit logs with pagination.
     */
    Page<StripeConfigAuditLog> findAllByOrderByChangedAtDesc(Pageable pageable);
    
    /**
     * Find audit logs by admin who made the change.
     */
    List<StripeConfigAuditLog> findByAdminIdOrderByChangedAtDesc(UUID adminId);
    
    /**
     * Find audit logs for a specific field.
     */
    List<StripeConfigAuditLog> findByFieldChangedOrderByChangedAtDesc(String fieldChanged);
    
    /**
     * Find audit logs within a date range.
     */
    @Query("""
        SELECT a FROM StripeConfigAuditLog a 
        WHERE a.changedAt BETWEEN :start AND :end 
        ORDER BY a.changedAt DESC
        """)
    List<StripeConfigAuditLog> findByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    
    /**
     * Get the most recent audit log entries (limited).
     */
    List<StripeConfigAuditLog> findTop20ByOrderByChangedAtDesc();
    
    /**
     * Count changes by a specific admin.
     */
    long countByAdminId(UUID adminId);
    
    /**
     * Find changes from a specific IP address.
     */
    List<StripeConfigAuditLog> findByIpAddressOrderByChangedAtDesc(String ipAddress);
}
