package com.marketmono.dto;

/**
 * Sealed interface for API response types to ensure type safety
 * All API responses must implement this interface
 */
public sealed interface ApiResponse<T> permits TickerTideResponse, MarketTideResponse {
    boolean success();
    T data();
    String timestamp();
    String error();
}