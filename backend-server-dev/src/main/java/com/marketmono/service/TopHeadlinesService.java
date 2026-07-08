package com.marketmono.service;

import com.marketmono.client.NewsApiClient;
import com.marketmono.dto.TopHeadlinesResponseDto;
import com.marketmono.exception.NewsApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for fetching top headlines from NewsAPI.org
 * Cached for 5 minutes (via Caffeine global spec) to reduce external API calls
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopHeadlinesService {

    private final NewsApiClient newsApiClient;

    /**
     * Fetch top headlines with optional filters.
     * Note: country/category cannot be mixed with sources per NewsAPI rules.
     */
    @Cacheable(
            value = "apiResponses",
            key = "'top-headlines-' + #country + '-' + #category + '-' + #sources + '-' + #q + '-' + #pageSize + '-' + #page",
            unless = "#result == null"
    )
    public TopHeadlinesResponseDto getTopHeadlines(
            String country,
            String category,
            String sources,
            String q,
            Integer pageSize,
            Integer page
    ) {
        log.debug("Fetching top headlines - country: {}, category: {}, sources: {}, q: {}",
                country, category, sources, q);

        try {
            TopHeadlinesResponseDto response = newsApiClient.getTopHeadlines(
                    country, category, sources, q, pageSize, page
            );

            if (response != null && response.articles() != null) {
                log.info("Retrieved {} top headlines (totalResults: {})",
                        response.articles().size(), response.totalResults());
            } else {
                log.warn("No top headlines returned from NewsAPI");
            }

            return response;
        } catch (Exception e) {
            log.error("Error fetching top headlines", e);
            throw new NewsApiRequestException("Failed to fetch top headlines: " + e.getMessage(), e);
        }
    }
}
