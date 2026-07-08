package com.marketmono.dto;

import java.util.List;

/**
 * Response wrapper for politician portfolios
 */
public record PoliticianPortfoliosResponse(
    List<PoliticianPortfolioDto> data
) {}
