package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Contract screener snapshots for one dashboard widget (Top Contracts, Top
 * ETF Orders, or Top Sweep Orders), keyed by the upstream order variant the
 * data was fetched with (premium / volume / stock_price).
 *
 * Served database-only from dashboard_contract_snapshots (populated by the
 * per-minute scheduler) and always reflects the latest stored trading day;
 * user requests never call the upstream market data API. The frontend picks
 * the variant that matches the selected sort filter locally, so filter
 * changes trigger no API calls.
 */
public record DashboardContractSnapshotsResponse(
    @JsonProperty("widget")
    String widget,

    @JsonProperty("tradingDate")
    String tradingDate,

    @JsonProperty("asOf")
    String asOf,

    @JsonProperty("orders")
    Map<String, List<OptionContractDto>> orders
) {}
