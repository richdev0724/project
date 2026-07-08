package com.marketmono.dto;

import java.util.List;

public record TopHeadlinesResponseDto(
    String status,
    Integer totalResults,
    List<TopHeadlineArticleDto> articles
) {}
