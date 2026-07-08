package com.marketmono.dto;

import java.util.List;

/**
 * Response from upstream market data API containing stock volume price levels
 * Endpoint: /api/stock/{ticker}/stock-volume-price-levels
 */
public record StockVolumePriceLevelsResponse(
    List<OffLitPriceLevelDto> data
) {}
