package com.marketmono.dto;

import java.util.List;

/**
 * Response wrapper for politicians list
 */
public record PoliticiansListResponse(
    List<PoliticianDto> data
) {}
