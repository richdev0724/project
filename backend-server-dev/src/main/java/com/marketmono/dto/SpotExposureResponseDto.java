package com.marketmono.dto;

import java.util.List;

/**
 * Wrapper for spot exposure API responses
 */
public record SpotExposureResponseDto(
    List<SpotExposureByStrikeDto> data
) {}





