package com.marketmono.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for updating the scheduled spot GEX ticker list.
 */
public record UpdateTickersRequest(
    @NotEmpty(message = "At least one ticker is required")
    @Size(max = 50, message = "At most 50 tickers may be configured")
    List<
        @NotBlank(message = "Ticker cannot be blank")
        @Pattern(
            regexp = "^[A-Za-z][A-Za-z0-9.\\-]{0,9}$",
            message = "Ticker must start with a letter and be 1-10 characters long"
        )
        String> tickers
) {}