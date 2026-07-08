package com.marketmono.dto;

import com.marketmono.entity.VenueType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO for individual dark pool/lit pool trade data.
 */
public record DpLpTradeDto(
    Long id,
    String ticker,
    VenueType venueType,
    BigDecimal price,
    Integer size,
    OffsetDateTime ts,
    String venue,
    String flags,
    String ingestSource
) {}
