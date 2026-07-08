package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Complete politician portfolio with all holdings
 * Matches upstream market data API response from /api/politician-portfolios/{politician_id}
 */
public record PoliticianPortfolioDto(
    UUID id,
    String owner,                      // "self", "spouse", "joint", "child", "undisclosed"
    
    @JsonProperty("last_annual_disclosure")
    Integer lastAnnualDisclosure,
    
    @JsonProperty("stock_holdings")
    List<StockHoldingDto> stockHoldings,
    
    @JsonProperty("option_holdings")
    List<OptionHoldingDto> optionHoldings,
    
    @JsonProperty("crypto_holdings")
    List<CryptoHoldingDto> cryptoHoldings
) {}
