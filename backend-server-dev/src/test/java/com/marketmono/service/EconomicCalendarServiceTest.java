package com.marketmono.service;

import com.marketmono.dto.EconEventDto;
import com.marketmono.dto.EconomicCalendarResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomicCalendarServiceTest {

    @Test
    void shouldBuildCalendarResponseFromCanonicalEconEventsService() {
        EconEventsService econEventsService = mock(EconEventsService.class);
        when(econEventsService.getEconEvents("2026-05-20", "2026-05-21")).thenReturn(Map.of(
            "2026-05-20",
            List.of(new EconEventDto(
                "14:00",
                "FOMC Minutes",
                "high",
                "4.50%",
                "4.50%",
                "fomc",
                "May",
                "2026-05-20T14:00:00Z"
            ))
        ));

        EconomicCalendarService service = new EconomicCalendarService(econEventsService, Clock.systemUTC());

        EconomicCalendarResponse response = service.getEconomicCalendar("2026-05-20", "2026-05-21", 7);

        assertThat(response.success()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().getFirst().dateKey()).isEqualTo("2026-05-20");
        assertThat(response.data().getFirst().events()).hasSize(1);
        EconomicCalendarResponse.EconomicEventItemDto event = response.data().getFirst().events().getFirst();
        assertThat(event.time()).isEqualTo("14:00");
        assertThat(event.title()).isEqualTo("FOMC Minutes");
        assertThat(event.priority()).isEqualTo("HIGH");
        assertThat(event.forecast()).isEqualTo("4.50%");
        assertThat(event.prev()).isEqualTo("4.50%");
        assertThat(event.type()).isEqualTo("fomc");
        assertThat(event.rawTime()).isEqualTo("2026-05-20T14:00:00Z");
        verify(econEventsService).getEconEvents("2026-05-20", "2026-05-21");
    }

    @Test
    void shouldUseDefaultCalendarRangeWhenDatesAreOmitted() {
        EconEventsService econEventsService = mock(EconEventsService.class);
        when(econEventsService.getEconEvents("2026-05-19", "2026-05-26")).thenReturn(Map.of());

        EconomicCalendarService service = new EconomicCalendarService(
            econEventsService,
            Clock.fixed(Instant.parse("2026-05-19T12:00:00Z"), ZoneOffset.UTC)
        );

        EconomicCalendarResponse response = service.getEconomicCalendar(null, null, 7);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEmpty();
        verify(econEventsService).getEconEvents("2026-05-19", "2026-05-26");
    }
}
