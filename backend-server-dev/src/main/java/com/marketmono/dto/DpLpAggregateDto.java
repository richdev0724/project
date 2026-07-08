package com.marketmono.dto;

import com.marketmono.entity.BucketSize;
import com.marketmono.entity.VenueType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO for aggregated dark pool/lit pool data.
 */
public record DpLpAggregateDto(
    String ticker,
    VenueType venueType,
    OffsetDateTime bucketStart,
    BucketSize bucketSize,
    Integer tradeCount,
    Long totalVolume,
    BigDecimal vwap,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Long netImbalance
) {}
