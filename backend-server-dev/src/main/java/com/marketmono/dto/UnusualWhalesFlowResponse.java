package com.marketmono.dto;

import java.util.List;

/**
 * Wrapper for upstream market data API response containing flow alerts
 */
public record UnusualWhalesFlowResponse(
    List<UnusualWhalesFlowAlertDto> data,
    String newerThan,
    String olderThan
) {}
