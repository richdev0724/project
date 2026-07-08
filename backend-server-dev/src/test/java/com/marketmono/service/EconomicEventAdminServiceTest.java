package com.marketmono.service;

import com.marketmono.dto.admin.AdminEconomicEventDto;
import com.marketmono.dto.admin.CreateEconomicEventRequest;
import com.marketmono.dto.admin.UpdateEconomicEventRequest;
import com.marketmono.entity.EconomicEvent;
import com.marketmono.entity.EconomicEventPriority;
import com.marketmono.entity.EconomicEventType;
import com.marketmono.repository.EconomicEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomicEventAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");

    @Mock
    private EconomicEventRepository economicEventRepository;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private EconomicEventAdminService service;

    @BeforeEach
    void setUp() {
        service = new EconomicEventAdminService(
            economicEventRepository,
            adminAuditLogService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createEventStoresManagedEconomicEvent() {
        UUID actorId = UUID.randomUUID();
        when(economicEventRepository.save(any(EconomicEvent.class))).thenAnswer(invocation -> {
            EconomicEvent event = invocation.getArgument(0);
            event.setId(42L);
            return event;
        });

        AdminEconomicEventDto created = service.createEvent(new CreateEconomicEventRequest(
            "CPI",
            "2026-05-20",
            "08:30",
            "HIGH",
            "REPORT",
            "0.3%",
            "0.2%",
            "Apr 2026"
        ), actorId);

        assertThat(created.id()).isEqualTo(42L);
        assertThat(created.event()).isEqualTo("CPI");
        assertThat(created.date()).isEqualTo("2026-05-20");
        assertThat(created.time()).isEqualTo("08:30");
        assertThat(created.importance()).isEqualTo("high");

        ArgumentCaptor<EconomicEvent> saved = ArgumentCaptor.forClass(EconomicEvent.class);
        verify(economicEventRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(EconomicEventType.REPORT);
        assertThat(saved.getValue().getPriority()).isEqualTo(EconomicEventPriority.HIGH);
        assertThat(saved.getValue().getCacheDate()).isEqualTo(LocalDate.parse("2026-05-20"));
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("ECONOMIC_EVENT_CREATED"),
            eq("ECONOMIC_EVENT"),
            eq("42"),
            argThat(details -> snapshotHasEvent(details, "after", "CPI"))
        );
    }

    @Test
    void createEventAcceptsExistingEconomicEventWireTypeValues() {
        UUID actorId = UUID.randomUUID();
        when(economicEventRepository.save(any(EconomicEvent.class))).thenAnswer(invocation -> {
            EconomicEvent event = invocation.getArgument(0);
            event.setId(43L);
            return event;
        });

        service.createEvent(new CreateEconomicEventRequest(
            "Fed Chair Speech",
            "2026-05-20",
            "12:00",
            "LOW",
            "fed-speaker",
            null,
            null,
            null
        ), actorId);

        ArgumentCaptor<EconomicEvent> saved = ArgumentCaptor.forClass(EconomicEvent.class);
        verify(economicEventRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(EconomicEventType.FED_SPEAKER);
    }

    @Test
    void createEventRejectsInvalidTypeAsBadRequest() {
        assertThatThrownBy(() -> service.createEvent(new CreateEconomicEventRequest(
            "Invalid type",
            "2026-05-20",
            "12:00",
            "LOW",
            "economic",
            null,
            null,
            null
        ), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid economic event type");
    }

    @Test
    void createEventRejectsInvalidImportanceAsBadRequest() {
        assertThatThrownBy(() -> service.createEvent(new CreateEconomicEventRequest(
            "Invalid importance",
            "2026-05-20",
            "12:00",
            "urgent",
            "REPORT",
            null,
            null,
            null
        ), UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid economic event importance");
    }

    @Test
    void updateEventChangesEditableFields() {
        UUID actorId = UUID.randomUUID();
        EconomicEvent event = existingEvent();
        when(economicEventRepository.findById(7L)).thenReturn(Optional.of(event));
        when(economicEventRepository.save(event)).thenReturn(event);

        AdminEconomicEventDto updated = service.updateEvent(7L, new UpdateEconomicEventRequest(
            "Core CPI",
            "2026-05-20",
            "08:31",
            "MEDIUM",
            "REPORT",
            "0.4%",
            "0.2%",
            "Apr 2026"
        ), actorId);

        assertThat(updated.event()).isEqualTo("Core CPI");
        assertThat(updated.time()).isEqualTo("08:31");
        assertThat(updated.importance()).isEqualTo("medium");
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("ECONOMIC_EVENT_UPDATED"),
            eq("ECONOMIC_EVENT"),
            eq("7"),
            argThat(details ->
                snapshotHasEvent(details, "before", "CPI")
                    && snapshotHasEvent(details, "after", "Core CPI"))
        );
    }

    @Test
    void retireEventHidesItWithoutDeletingTheRow() {
        UUID actorId = UUID.randomUUID();
        EconomicEvent event = existingEvent();
        when(economicEventRepository.findById(7L)).thenReturn(Optional.of(event));
        when(economicEventRepository.save(event)).thenReturn(event);

        AdminEconomicEventDto retired = service.retireEvent(7L, actorId);

        assertThat(retired.retired()).isTrue();
        assertThat(event.getRetiredAt()).isEqualTo(NOW);
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("ECONOMIC_EVENT_RETIRED"),
            eq("ECONOMIC_EVENT"),
            eq("7"),
            argThat(details ->
                snapshotHasEvent(details, "before", "CPI")
                    && snapshotFieldEquals(details, "after", "retiredAt", NOW.toString()))
        );
    }

    @Test
    void deleteEventRemovesRowAndRecordsAuditLog() {
        UUID actorId = UUID.randomUUID();
        EconomicEvent event = existingEvent();
        when(economicEventRepository.findById(7L)).thenReturn(Optional.of(event));

        service.deleteEvent(7L, actorId);

        verify(economicEventRepository).delete(event);
        verify(adminAuditLogService).recordEvent(
            eq(actorId),
            eq("ECONOMIC_EVENT_DELETED"),
            eq("ECONOMIC_EVENT"),
            eq("7"),
            argThat(details -> snapshotHasEvent(details, "before", "CPI"))
        );
    }

    @Test
    void listEventsUsesRepositoryDateRangeAndRetiredFilter() {
        EconomicEvent event = existingEvent();
        when(economicEventRepository.findByCacheDateBetweenAndRetiredAtIsNullOrderByEventTimeAsc(
            LocalDate.parse("2026-05-20"),
            LocalDate.parse("2026-05-21")
        )).thenReturn(List.of(event));

        List<AdminEconomicEventDto> events = service.listEvents(
            LocalDate.parse("2026-05-20"),
            LocalDate.parse("2026-05-21"),
            false
        );

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().event()).isEqualTo("CPI");
    }

    private EconomicEvent existingEvent() {
        return EconomicEvent.builder()
            .id(7L)
            .event("CPI")
            .eventTime(Instant.parse("2026-05-20T08:30:00Z"))
            .priority(EconomicEventPriority.HIGH)
            .type(EconomicEventType.REPORT)
            .forecast("0.3%")
            .prev("0.2%")
            .reportedPeriod("Apr 2026")
            .cacheDate(LocalDate.parse("2026-05-20"))
            .cachedAt(NOW)
            .build();
    }

    private boolean snapshotHasEvent(Map<String, Object> details, String key, String expectedEvent) {
        return snapshotFieldEquals(details, key, "event", expectedEvent);
    }

    private boolean snapshotFieldEquals(Map<String, Object> details, String key, String field, String expectedValue) {
        if (details == null || !(details.get(key) instanceof Map<?, ?> snapshot)) {
            return false;
        }
        return expectedValue.equals(snapshot.get(field));
    }
}
