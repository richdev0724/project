package com.marketmono.dto;

import java.util.List;

/**
 * Response DTO for economic calendar endpoint
 * Groups events by date with priority classification
 */
public record EconomicCalendarResponse(
    boolean success,
    List<EconomicEventDayDto> data,
    String error
) {
    public static EconomicCalendarResponse success(List<EconomicEventDayDto> data) {
        return new EconomicCalendarResponse(true, data, null);
    }
    
    public static EconomicCalendarResponse error(String error) {
        return new EconomicCalendarResponse(false, null, error);
    }
    
    /**
     * Events grouped by date
     */
    public record EconomicEventDayDto(
        String date,
        String dateKey,
        List<EconomicEventItemDto> events
    ) {}
    
    /**
     * Individual event with priority and formatting
     */
    public record EconomicEventItemDto(
        String time,
        String title,
        String priority,
        String forecast,
        String prev,
        String type,
        String rawTime
    ) {}
}
