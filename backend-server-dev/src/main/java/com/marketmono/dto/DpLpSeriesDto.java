package com.marketmono.dto;

import com.marketmono.entity.BucketSize;
import com.marketmono.entity.VenueType;

import java.util.List;

/**
 * DTO for DP/LP series data (chart time series).
 */
public record DpLpSeriesDto(
    String ticker,
    VenueType venueType,
    BucketSize bucketSize,
    List<DpLpAggregateDto> series
) {}
