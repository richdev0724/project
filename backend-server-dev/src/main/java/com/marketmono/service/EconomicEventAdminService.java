package com.marketmono.service;

import com.marketmono.dto.admin.AdminEconomicEventDto;
import com.marketmono.dto.admin.CreateEconomicEventRequest;
import com.marketmono.dto.admin.UpdateEconomicEventRequest;
import com.marketmono.entity.EconomicEvent;
import com.marketmono.entity.EconomicEventPriority;
import com.marketmono.entity.EconomicEventType;
import com.marketmono.repository.EconomicEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class EconomicEventAdminService {

    private static final String RESOURCE_TYPE = "ECONOMIC_EVENT";
    private static final String ACTION_CREATED = "ECONOMIC_EVENT_CREATED";
    private static final String ACTION_UPDATED = "ECONOMIC_EVENT_UPDATED";
    private static final String ACTION_RETIRED = "ECONOMIC_EVENT_RETIRED";
    private static final String ACTION_DELETED = "ECONOMIC_EVENT_DELETED";

    private final EconomicEventRepository economicEventRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Autowired
    public EconomicEventAdminService(
            EconomicEventRepository economicEventRepository,
            AdminAuditLogService adminAuditLogService) {
        this(economicEventRepository, adminAuditLogService, Clock.systemUTC());
    }

    EconomicEventAdminService(
            EconomicEventRepository economicEventRepository,
            AdminAuditLogService adminAuditLogService,
            Clock clock) {
        this.economicEventRepository = economicEventRepository;
        this.adminAuditLogService = adminAuditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminEconomicEventDto> listEvents(LocalDate startDate, LocalDate endDate, boolean includeRetired) {
        LocalDate start = startDate != null ? startDate : LocalDate.now(clock).minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now(clock).plusDays(90);
        List<EconomicEvent> events = includeRetired
            ? economicEventRepository.findByCacheDateBetweenOrderByEventTimeAsc(start, end)
            : economicEventRepository.findByCacheDateBetweenAndRetiredAtIsNullOrderByEventTimeAsc(start, end);
        return events.stream().map(this::toDto).toList();
    }

    @Transactional
    public AdminEconomicEventDto createEvent(CreateEconomicEventRequest request, UUID actorId) {
        EconomicEvent event = EconomicEvent.builder()
            .event(normalizeRequired(request.event(), "Event is required"))
            .eventTime(parseEventTime(request.date(), request.time()))
            .priority(parsePriority(request.importance()))
            .type(parseType(request.type()))
            .forecast(normalizeNullable(request.forecast()))
            .prev(normalizeNullable(request.previous()))
            .reportedPeriod(normalizeNullable(request.reportedPeriod()))
            .cacheDate(LocalDate.parse(request.date()))
            .cachedAt(Instant.now(clock))
            .build();
        event = economicEventRepository.save(event);
        recordAudit(actorId, ACTION_CREATED, event, details(null, event));
        log.info("Economic event created by admin actor {}: {}", actorId, event.getEvent());
        return toDto(event);
    }

    @Transactional
    public AdminEconomicEventDto updateEvent(Long eventId, UpdateEconomicEventRequest request, UUID actorId) {
        EconomicEvent event = requireEvent(eventId);
        Map<String, Object> before = snapshot(event);
        event.setEvent(normalizeRequired(request.event(), "Event is required"));
        event.setEventTime(parseEventTime(request.date(), request.time()));
        event.setPriority(parsePriority(request.importance()));
        event.setType(parseType(request.type()));
        event.setForecast(normalizeNullable(request.forecast()));
        event.setPrev(normalizeNullable(request.previous()));
        event.setReportedPeriod(normalizeNullable(request.reportedPeriod()));
        event.setCacheDate(LocalDate.parse(request.date()));
        event = economicEventRepository.save(event);
        recordAudit(actorId, ACTION_UPDATED, event, details(before, event));
        log.info("Economic event {} updated by admin actor {}", eventId, actorId);
        return toDto(event);
    }

    @Transactional
    public AdminEconomicEventDto retireEvent(Long eventId, UUID actorId) {
        EconomicEvent event = requireEvent(eventId);
        Map<String, Object> before = snapshot(event);
        event.setRetiredAt(Instant.now(clock));
        event = economicEventRepository.save(event);
        recordAudit(actorId, ACTION_RETIRED, event, details(before, event));
        log.info("Economic event {} retired by admin actor {}", eventId, actorId);
        return toDto(event);
    }

    @Transactional
    public void deleteEvent(Long eventId, UUID actorId) {
        EconomicEvent event = requireEvent(eventId);
        Map<String, Object> before = snapshot(event);
        economicEventRepository.delete(event);
        recordAudit(actorId, ACTION_DELETED, event, details(before, null));
        log.info("Economic event {} deleted by admin actor {}", eventId, actorId);
    }

    private EconomicEvent requireEvent(Long eventId) {
        return economicEventRepository.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Economic event not found."));
    }

    private AdminEconomicEventDto toDto(EconomicEvent event) {
        return new AdminEconomicEventDto(
            event.getId(),
            event.getEvent(),
            event.getCacheDate().toString(),
            LocalTime.ofInstant(event.getEventTime(), ZoneOffset.UTC).toString(),
            event.getPriority().name().toLowerCase(),
            event.getType().name(),
            event.getForecast(),
            event.getPrev(),
            event.getReportedPeriod(),
            event.getRetiredAt() != null
        );
    }

    private Instant parseEventTime(String date, String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(ZoneOffset.UTC);
    }

    private void recordAudit(UUID actorId, String action, EconomicEvent event, Map<String, Object> details) {
        adminAuditLogService.recordEvent(
            actorId,
            action,
            RESOURCE_TYPE,
            event.getId() != null ? event.getId().toString() : null,
            details
        );
    }

    private Map<String, Object> details(Map<String, Object> before, EconomicEvent after) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (before != null) {
            details.put("before", before);
        }
        if (after != null) {
            details.put("after", snapshot(after));
        }
        return details;
    }

    private Map<String, Object> snapshot(EconomicEvent event) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", event.getId());
        snapshot.put("event", event.getEvent());
        snapshot.put("date", event.getCacheDate() != null ? event.getCacheDate().toString() : null);
        snapshot.put("time", event.getEventTime() != null
            ? LocalTime.ofInstant(event.getEventTime(), ZoneOffset.UTC).toString()
            : null);
        snapshot.put("eventTime", event.getEventTime() != null ? event.getEventTime().toString() : null);
        snapshot.put("importance", event.getPriority() != null ? event.getPriority().name().toLowerCase() : null);
        snapshot.put("type", event.getType() != null ? event.getType().name() : null);
        snapshot.put("forecast", event.getForecast());
        snapshot.put("previous", event.getPrev());
        snapshot.put("reportedPeriod", event.getReportedPeriod());
        snapshot.put("retiredAt", event.getRetiredAt() != null ? event.getRetiredAt().toString() : null);
        return snapshot;
    }

    private EconomicEventPriority parsePriority(String value) {
        try {
            return EconomicEventPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid economic event importance.", ex);
        }
    }

    private EconomicEventType parseType(String value) {
        String normalized = value.trim();
        try {
            return EconomicEventType.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            try {
                return EconomicEventType.valueOf(normalized.toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid economic event type.", ex);
            }
        }
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
