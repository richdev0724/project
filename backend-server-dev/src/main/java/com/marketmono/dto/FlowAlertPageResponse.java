package com.marketmono.dto;

import java.util.List;

/**
 * Paginated response wrapper for flow alerts (frontend format)
 */
public record FlowAlertPageResponse(
    List<FlowAlertResponseDto> results,
    long totalCount
) {}
