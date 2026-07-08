package com.marketmono.dto;

import java.util.UUID;

/**
 * Basic politician information
 * Matches upstream market data API "Politician" schema from /api/politician-portfolios/people
 * Enhanced with party affiliation data from Congress.gov API
 */
public record PoliticianDto(
    UUID id,
    String name,
    String party  // D (Democratic), R (Republican), I (Independent), UNK (Unknown)
) {}
