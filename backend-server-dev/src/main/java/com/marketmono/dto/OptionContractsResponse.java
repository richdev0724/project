package com.marketmono.dto;

import java.util.List;

/**
 * Response wrapper for option contracts screener API
 */
public record OptionContractsResponse(
    List<OptionContractDto> data
) {}


