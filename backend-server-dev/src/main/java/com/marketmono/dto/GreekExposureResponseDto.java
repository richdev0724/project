package com.marketmono.dto;

import java.util.List;

/**
 * Wrapper for greek exposure API responses
 */
public record GreekExposureResponseDto<T>(
    List<T> data
) {}
