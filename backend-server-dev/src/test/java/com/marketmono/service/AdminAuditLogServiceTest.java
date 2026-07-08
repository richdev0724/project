package com.marketmono.service;

import com.marketmono.entity.AdminAuditLog;
import com.marketmono.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    private AdminAuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AdminAuditLogService(adminAuditLogRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordPersistsSingleAdminAuditRow() {
        UUID adminId = UUID.randomUUID();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("after", Map.of("event", "CPI"));
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordEvent(adminId, "ECONOMIC_EVENT_CREATED", "ECONOMIC_EVENT", "42", details);

        ArgumentCaptor<AdminAuditLog> saved = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getAdminUserId()).isEqualTo(adminId);
        assertThat(saved.getValue().getAction()).isEqualTo("ECONOMIC_EVENT_CREATED");
        assertThat(saved.getValue().getResourceType()).isEqualTo("ECONOMIC_EVENT");
        assertThat(saved.getValue().getResourceId()).isEqualTo("42");
        assertThat(saved.getValue().getDetails()).containsEntry("after", Map.of("event", "CPI"));
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void recordRequiresAdminActorId() {
        assertThatThrownBy(() -> service.recordEvent(
            null,
            "ECONOMIC_EVENT_CREATED",
            "ECONOMIC_EVENT",
            "42",
            Map.of()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Admin actor id is required");
    }
}
