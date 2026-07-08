package com.marketmono.dto;

import java.util.List;

/**
 * Paginated response wrapper for option trades (frontend format).
 */
public record OptionTradePageResponse(
    List<OptionTradeDto> results,
    Long totalCount,
    boolean hasNext,
    boolean totalCountExact,
    OptionTradeCountMode countMode
) {

    public static OptionTradePageResponse exact(List<OptionTradeDto> results, long totalCount, boolean hasNext) {
        return new OptionTradePageResponse(results, totalCount, hasNext, true, OptionTradeCountMode.EXACT);
    }

    public static OptionTradePageResponse slice(List<OptionTradeDto> results, boolean hasNext) {
        return new OptionTradePageResponse(results, null, hasNext, false, OptionTradeCountMode.SLICE);
    }
}
