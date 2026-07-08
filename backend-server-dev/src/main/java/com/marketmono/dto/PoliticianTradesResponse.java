package com.marketmono.dto;

import java.util.List;

/**
 * Response wrapper for politician trades list
 */
public record PoliticianTradesResponse(
    List<PoliticianTradeDto> data
) {}
