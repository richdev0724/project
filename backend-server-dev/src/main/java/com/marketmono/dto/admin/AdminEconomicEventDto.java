package com.marketmono.dto.admin;

public record AdminEconomicEventDto(
    Long id,
    String event,
    String date,
    String time,
    String importance,
    String type,
    String forecast,
    String previous,
    String reportedPeriod,
    boolean retired
) {
}
