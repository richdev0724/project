package com.marketmono.service;

import com.marketmono.dto.EconEventDto;
import com.marketmono.dto.EconomicCalendarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Temporary compatibility adapter for the legacy economic-calendar route.
 * The canonical economic-events data path is EconEventsService.
 */
@Service
@Slf4j
public class EconomicCalendarService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    private final EconEventsService econEventsService;
    private final Clock clock;

    @Autowired
    public EconomicCalendarService(EconEventsService econEventsService) {
        this(econEventsService, Clock.systemUTC());
    }

    EconomicCalendarService(EconEventsService econEventsService, Clock clock) {
        this.econEventsService = econEventsService;
        this.clock = clock;
    }
    
    /**
     * Get economic calendar events with optional date range
     * 
     * @param startDate Optional start date (YYYY-MM-DD) - used for filtering
     * @param endDate Optional end date (YYYY-MM-DD) - used for filtering
     * @param days Number of days ahead to fetch (default 7) - used for filtering
     * @return EconomicCalendarResponse containing grouped events by date
     */
    public EconomicCalendarResponse getEconomicCalendar(String startDate, String endDate, int days) {
        try {
            log.info("[ECONOMIC-CALENDAR] Fetching managed events - startDate: {}, endDate: {}, days: {}",
                startDate, endDate, days);
            
            LocalDate start = startDate != null ? LocalDate.parse(startDate, DATE_FORMATTER) : LocalDate.now(clock);
            LocalDate end = endDate != null ? LocalDate.parse(endDate, DATE_FORMATTER) : start.plusDays(Math.max(days, 0));

            Map<String, List<EconEventDto>> canonicalEvents = econEventsService.getEconEvents(
                start.format(DATE_FORMATTER),
                end.format(DATE_FORMATTER)
            );
            
            List<EconomicCalendarResponse.EconomicEventDayDto> groupedEvents = toCompatibilityResponse(canonicalEvents);
            
            log.info("[ECONOMIC-CALENDAR] Processed {} days from canonical /econ_events data", groupedEvents.size());
            
            return EconomicCalendarResponse.success(groupedEvents);
            
        } catch (Exception e) {
            log.error("[ECONOMIC-CALENDAR] Error fetching data: {}", e.getMessage(), e);
            return EconomicCalendarResponse.error(e.getMessage());
        }
    }
    
    private List<EconomicCalendarResponse.EconomicEventDayDto> toCompatibilityResponse(
            Map<String, List<EconEventDto>> eventsByDate) {
        return eventsByDate.entrySet()
            .stream()
            .map(entry -> {
                LocalDate date = LocalDate.parse(entry.getKey(), DATE_FORMATTER);
                List<EconomicCalendarResponse.EconomicEventItemDto> sortedEvents = entry.getValue().stream()
                    .map(event -> toCompatibilityEvent(entry.getKey(), event))
                    .sorted(Comparator.comparing(EconomicCalendarResponse.EconomicEventItemDto::rawTime))
                    .toList();
                
                return new EconomicCalendarResponse.EconomicEventDayDto(
                    formatDateString(date),
                    date.format(DATE_FORMATTER),
                    sortedEvents
                );
            })
            .sorted(Comparator.comparing(EconomicCalendarResponse.EconomicEventDayDto::dateKey))
            .toList();
    }

    private EconomicCalendarResponse.EconomicEventItemDto toCompatibilityEvent(String dateKey, EconEventDto event) {
        return new EconomicCalendarResponse.EconomicEventItemDto(
            event.time(),
            event.event(),
            event.importance() != null ? event.importance().toUpperCase() : "LOW",
            event.forecast() != null ? event.forecast() : "",
            event.previous() != null ? event.previous() : "",
            event.type() != null ? event.type() : "report",
            event.rawTime() != null ? event.rawTime() : dateKey + "T" + normalizeTime(event.time()) + "Z"
        );
    }

    private String normalizeTime(String time) {
        LocalTime parsed = LocalTime.parse(time, TIME_FORMATTER);
        return parsed.toSecondOfDay() % 60 == 0 ? parsed + ":00" : parsed.toString();
    }
    
    /**
     * Format date as "Wednesday, February 5th"
     */
    private String formatDateString(LocalDate date) {
        String weekday = date.getDayOfWeek().toString().charAt(0) + 
                        date.getDayOfWeek().toString().substring(1).toLowerCase();
        String month = date.getMonth().toString().charAt(0) + 
                      date.getMonth().toString().substring(1).toLowerCase();
        int day = date.getDayOfMonth();
        
        String suffix = switch (day) {
            case 1, 21, 31 -> "st";
            case 2, 22 -> "nd";
            case 3, 23 -> "rd";
            default -> "th";
        };
        
        return weekday + ", " + month + " " + day + suffix;
    }
}
