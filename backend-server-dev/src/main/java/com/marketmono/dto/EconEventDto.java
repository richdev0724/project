package com.marketmono.dto;

public record EconEventDto(
        String time,
        String event,
        String importance,
        String forecast,
        String previous,
        String type,
        String reportedPeriod,
        String rawTime
) {
    public EconEventDto(String time, String event, String importance) {
        this(time, event, importance, null, null, null, null, null);
    }
}
