package com.marketmono.dto;

import java.util.List;

/**
 * Wrapper for news headlines API response
 */
public record NewsResponseDto(
    List<NewsHeadlineDto> data,
    Boolean success,
    String error
) {
    public NewsResponseDto(List<NewsHeadlineDto> data) {
        this(data, null, null);
    }

    public static NewsResponseDto success(List<NewsHeadlineDto> data) {
        return new NewsResponseDto(data, true, null);
    }

    public static NewsResponseDto failure(String error) {
        return new NewsResponseDto(List.of(), false, error);
    }
}
