package com.marketmono.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEconomicEventRequest(
    @NotBlank(message = "Event is required")
    @Size(max = 500, message = "Event must be 500 characters or less")
    String event,

    @NotBlank(message = "Date is required")
    String date,

    @NotBlank(message = "Time is required")
    String time,

    @NotBlank(message = "Importance is required")
    String importance,

    @NotBlank(message = "Type is required")
    String type,

    @Size(max = 100, message = "Forecast must be 100 characters or less")
    String forecast,

    @Size(max = 100, message = "Previous value must be 100 characters or less")
    String previous,

    @Size(max = 100, message = "Reported period must be 100 characters or less")
    String reportedPeriod
) {
}
