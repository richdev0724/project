package com.marketmono.service;

import com.marketmono.dto.EconEventDto;
import com.marketmono.entity.EconomicEvent;
import com.marketmono.entity.EconomicEventPriority;
import com.marketmono.entity.EconomicEventType;
import com.marketmono.repository.EconomicEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconEventsServiceTest {

    @Test
    void shouldServeEconomicEventsFromDatabaseOnly() {
        EconomicEventRepository repository = mock(EconomicEventRepository.class);
        EconomicEvent managedEvent = EconomicEvent.builder()
            .id(1L)
            .event("CPI")
            .eventTime(Instant.parse("2026-05-20T08:30:00Z"))
            .priority(EconomicEventPriority.HIGH)
            .type(EconomicEventType.REPORT)
            .cacheDate(LocalDate.parse("2026-05-20"))
            .cachedAt(Instant.parse("2026-05-19T10:00:00Z"))
            .build();
        when(repository.findActiveByOptionalCacheDateRange(
            LocalDate.parse("2026-05-20"),
            LocalDate.parse("2026-05-21")
        )).thenReturn(List.of(managedEvent));

        EconEventsService service = new EconEventsService(repository);

        Map<String, List<EconEventDto>> events = service.getEconEvents("2026-05-20", "2026-05-21");

        assertThat(events).containsOnlyKeys("2026-05-20");
        assertThat(events.get("2026-05-20")).containsExactly(new EconEventDto(
            "08:30",
            "CPI",
            "high",
            null,
            null,
            "report",
            null,
            "2026-05-20T08:30:00Z"
        ));
        verify(repository).findActiveByOptionalCacheDateRange(
            LocalDate.parse("2026-05-20"),
            LocalDate.parse("2026-05-21")
        );
    }

    @Test
    void shouldReturnEmptyMapWhenDatabaseHasNoEvents() {
        EconomicEventRepository repository = mock(EconomicEventRepository.class);
        when(repository.findActiveByOptionalCacheDateRange(null, null)).thenReturn(List.of());

        EconEventsService service = new EconEventsService(repository);

        assertThat(service.getEconEvents(null, null)).isEmpty();
    }

    @Test
    void shouldRejectInvalidDateFiltersBeforeQueryingDatabase() {
        EconomicEventRepository repository = mock(EconomicEventRepository.class);
        EconEventsService service = new EconEventsService(repository);

        assertThatThrownBy(() -> service.getEconEvents("not-a-date", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid economic event date");
    }
}
