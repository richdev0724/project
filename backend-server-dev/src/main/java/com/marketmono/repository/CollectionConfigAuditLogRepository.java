package com.marketmono.repository;

import com.marketmono.entity.CollectionConfigAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for collection configuration audit history.
 */
@Repository
public interface CollectionConfigAuditLogRepository extends JpaRepository<CollectionConfigAuditLog, UUID> {

    List<CollectionConfigAuditLog> findTop20ByConfigKeyOrderByChangedAtDesc(String configKey);

    List<CollectionConfigAuditLog> findAllByConfigKeyOrderByChangedAtDesc(String configKey);
}