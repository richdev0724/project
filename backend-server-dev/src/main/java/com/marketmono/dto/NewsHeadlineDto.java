package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * News headline from upstream market data API
 * Maps to /api/news/headlines response structure
 */
public record NewsHeadlineDto(
    @JsonProperty("created_at")
    OffsetDateTime createdAt,
    
    String headline,
    
    String source,
    
    List<String> tickers,
    
    String sentiment,
    
    @JsonProperty("is_major")
    Boolean isMajor,
    
    List<String> tags,
    
    Map<String, Object> meta,

    String description,

    String url,

    @JsonProperty("image_url")
    String imageUrl
) {
    public NewsHeadlineDto(
        OffsetDateTime createdAt,
        String headline,
        String source,
        List<String> tickers,
        String sentiment,
        Boolean isMajor,
        List<String> tags,
        Map<String, Object> meta
    ) {
        this(createdAt, headline, source, tickers, sentiment, isMajor, tags, meta, null, null, null);
    }
}
