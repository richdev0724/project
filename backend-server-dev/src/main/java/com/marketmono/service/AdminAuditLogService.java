package com.marketmono.service;

import com.marketmono.entity.AdminAuditLog;
import com.marketmono.repository.AdminAuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final Clock clock;

    @Autowired
    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
        this(adminAuditLogRepository, Clock.systemUTC());
    }

    AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository, Clock clock) {
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminAuditLog recordEvent(
            UUID adminUserId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details) {
        Objects.requireNonNull(adminUserId, "Admin actor id is required for audit log entries");
        Objects.requireNonNull(action, "Admin audit action is required");
        Objects.requireNonNull(resourceType, "Admin audit resource type is required");

        return adminAuditLogRepository.save(AdminAuditLog.builder()
            .adminUserId(adminUserId)
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .details(copyDetails(details))
            .createdAt(Instant.now(clock))
            .build());
    }

    private Map<String, Object> copyDetails(Map<String, Object> details) {
        return details == null ? Map.of() : new LinkedHashMap<>(details);
    }
}
