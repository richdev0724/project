package com.marketmono.dto;

import java.util.List;

/**
 * Response from upstream market data provider Congress Trades API
 * Endpoint: GET /api/congress/recent-trades
 */
public record CongressTradesResponse(
    List<CongressTrade> data
) {}
