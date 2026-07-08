package com.marketmono.dto;

import java.util.List;

/**
 * DTO for earnings by date response from upstream market data API
 */
public record EarningsByDateResponseDto(
    String date,
    List<EarningsItemDto> premarket,
    List<EarningsItemDto> afterhours,
    Boolean success
) {}