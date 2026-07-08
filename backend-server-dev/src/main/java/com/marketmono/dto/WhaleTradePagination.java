package com.marketmono.dto;

/**
 * Pagination information for whale trades response
 */
public record WhaleTradePagination(
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    boolean hasNext,
    boolean hasPrev
) {}
