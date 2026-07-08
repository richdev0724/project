package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketauxNewsClient {

    private static final int DEFAULT_LIMIT = 20;

    private final WebClient webClient;

    @Value("${marketaux.api-token:}")
    private String apiToken;

    @Value("${marketaux.base-url:https://api.marketaux.com}")
    private String baseUrl;

    public NewsResponseDto getHeadlines() {
        if (apiToken == null || apiToken.isBlank()) {
            return NewsResponseDto.failure("Marketaux API token is not configured");
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/v1/news/all")
                .queryParam("categories", "business,finance,markets")
                .queryParam("language", "en")
                .queryParam("limit", DEFAULT_LIMIT)
                .queryParam("api_token", apiToken)
                .build()
                .toUri();

            JsonNode body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            return NewsResponseDto.success(mapResults(body));
        } catch (Exception e) {
            log.warn("Marketaux news provider failed", e);
            return NewsResponseDto.failure(e.getMessage());
        }
    }

    static List<NewsHeadlineDto> mapResults(JsonNode body) {
        JsonNode results = body == null ? null : body.path("data");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<NewsHeadlineDto> headlines = new ArrayList<>();
        for (JsonNode article : results) {
            String title = text(article, "title");
            if (title.isBlank()) {
                continue;
            }
            headlines.add(new NewsHeadlineDto(
                parseOffsetDateTime(text(article, "published_at")),
                title,
                source(article),
                entitySymbols(article.path("entities")),
                "",
                false,
                stringArray(article.path("keywords")),
                Map.of("provider", "marketaux"),
                firstNonBlank(text(article, "snippet"), text(article, "description")),
                text(article, "url"),
                text(article, "image_url")
            ));
        }
        return headlines;
    }

    private static String source(JsonNode article) {
        JsonNode source = article.path("source");
        if (source.isTextual()) {
            return firstNonBlank(source.asText(), "Marketaux");
        }
        return firstNonBlank(text(source, "name"), text(source, "domain"), "Marketaux");
    }

    private static List<String> entitySymbols(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String symbol = firstNonBlank(text(item, "symbol"), text(item, "name"));
            if (!symbol.isBlank()) {
                values.add(symbol);
            }
        }
        return List.copyOf(values);
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private static List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
