package com.marketmono.dto;

import java.util.List;

/**
 * Paginated response for whale trades
 */
public record WhaleTradePageResponse(
    boolean success,
    List<WhaleTradeDto> data,
    WhaleTradePagination pagination,
    WhaleTradeFilters filters
) {
    public WhaleTradePageResponse(List<WhaleTradeDto> data, WhaleTradePagination pagination, WhaleTradeFilters filters) {
        this(true, data, pagination, filters);
    }
}
