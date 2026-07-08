package com.marketmono.client;

import com.marketmono.dto.TopHeadlinesResponseDto;
import com.marketmono.exception.NewsApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * HTTP client for NewsAPI.org
 * Fetches top headlines via /v2/top-headlines
 *
 * API docs: https://newsapi.org/docs/endpoints/top-headlines
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiClient {

    private final WebClient webClient;

    @Value("${newsapi.api-key:}")
    private String apiKey;

    @Value("${newsapi.base-url:https://newsapi.org}")
    private String baseUrl;

    /**
     * Fetch top headlines with optional filters.
     * At least one of country, category, sources, or q must be provided by the caller.
     */
    public TopHeadlinesResponseDto getTopHeadlines(
            String country,
            String category,
            String sources,
            String q,
            Integer pageSize,
            Integer page
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new NewsApiRequestException("NewsAPI API key is not configured");
        }

        log.debug("Fetching top headlines from NewsAPI - country: {}, category: {}, sources: {}, q: {}",
                country, category, sources, q);

        try {
            return webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("newsapi.org")
                                .path("/v2/top-headlines")
                                .queryParam("apiKey", apiKey);

                        if (country != null && !country.isBlank()) {
                            builder.queryParam("country", country);
                        }
                        if (category != null && !category.isBlank()) {
                            builder.queryParam("category", category);
                        }
                        if (sources != null && !sources.isBlank()) {
                            builder.queryParam("sources", sources);
                        }
                        if (q != null && !q.isBlank()) {
                            builder.queryParam("q", q);
                        }
                        if (pageSize != null) {
                            builder.queryParam("pageSize", pageSize);
                        }
                        if (page != null) {
                            builder.queryParam("page", page);
                        }

                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(TopHeadlinesResponseDto.class)
                    .doOnError(error -> log.error("Failed to fetch top headlines from NewsAPI", error))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("NewsAPI returned error status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new NewsApiRequestException("NewsAPI request failed: " + e.getMessage(), e);
        }
    }
}
