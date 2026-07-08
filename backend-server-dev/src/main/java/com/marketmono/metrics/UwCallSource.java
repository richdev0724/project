package com.marketmono.metrics;

public enum UwCallSource {
    SCHEDULER_SPOT_GEX("scheduler_spot_gex"),
    CACHE_WARMER_MARKET_TIDE("cache_warmer_market_tide"),
    CACHE_WARMER_SECTOR("cache_warmer_sector"),
    USER_REQUEST("user_request"),
    ADMIN_REQUEST("admin_request"),
    FALLBACK_SYNC("fallback_sync"),
    UNKNOWN("unknown");

    private final String metricValue;

    UwCallSource(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
