package com.marketmono.dto;

import java.util.List;

public record OptionTradeAggregatePageResponse(
    List<OptionTradeAggregateDto> results,
    Long totalCount,
    boolean hasNext,
    boolean totalCountExact,
    OptionTradeCountMode countMode
) {}
