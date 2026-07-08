package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sector performance response for the dashboard Sectors widget.
 * Served database-only from sector_performance_snapshots (populated by the
 * per-minute scheduler); user requests never call the upstream market data API.
 *
 * Daily metrics come from the stock-state endpoint: dailyPct/dailyChange are
 * the % and dollar difference between close and prev_close. The 30d/6m metrics
 * compare the live close against baseline closes from daily OHLC history.
 */
public record SectorPerformanceLiveResponse(
    String ticker,

    @JsonProperty("lastClose")
    Double lastClose,

    @JsonProperty("prevClose")
    Double prevClose,

    @JsonProperty("dailyPct")
    Double dailyPct,

    @JsonProperty("dailyChange")
    Double dailyChange,

    @JsonProperty("pct30d")
    Double pct30d,

    @JsonProperty("change30d")
    Double change30d,

    @JsonProperty("pct6m")
    Double pct6m,

    @JsonProperty("change6m")
    Double change6m,

    @JsonProperty("volume")
    Long volume,

    @JsonProperty("lastDate")
    String lastDate
) {}
