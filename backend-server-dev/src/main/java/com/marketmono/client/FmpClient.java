package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.dto.CompanyProfileDto;
import com.marketmono.dto.TickerSearchResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class FmpClient {

    private final WebClient webClient;

    @Value("${fmp.api-key:}")
    private String apiKey;

    @Value("${fmp.base-url:https://financialmodelingprep.com}")
    private String baseUrl;

    private static final int SEARCH_LIMIT = 25;

    public List<TickerSearchResultDto> searchSymbols(String query) {
        return search("/stable/search-symbol", query, "symbol search");
    }

    public List<TickerSearchResultDto> searchByCompanyName(String query) {
        return search("/stable/search-name", query, "company search");
    }

    public CompanyProfileDto getCompanyProfile(String ticker) {
        String normalizedTicker = ticker == null ? "" : ticker.toUpperCase(Locale.ROOT).trim();
        if (normalizedTicker.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/stable/profile")
                .queryParam("symbol", normalizedTicker)
                .queryParam("apikey", apiKey)
                .build()
                .toUri();
            JsonNode response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || !response.isArray() || response.isEmpty()) {
                return null;
            }
            return mapProfile(response.get(0));
        } catch (Exception e) {
            log.warn("Failed to fetch FMP company profile for {}", normalizedTicker, e);
            return null;
        }
    }

    private List<TickerSearchResultDto> search(String path, String query, String label) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank() || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("query", normalizedQuery)
                .queryParam("limit", SEARCH_LIMIT)
                .queryParam("apikey", apiKey)
                .build()
                .toUri();
            JsonNode response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response == null || !response.isArray() || response.isEmpty()) {
                return List.of();
            }

            List<TickerSearchResultDto> results = new ArrayList<>();
            for (JsonNode node : response) {
                TickerSearchResultDto mapped = mapSearchResult(node);
                if (mapped != null) {
                    results.add(mapped);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed FMP {} for query '{}'", label, normalizedQuery, e);
            return List.of();
        }
    }

    private static TickerSearchResultDto mapSearchResult(JsonNode node) {
        String symbol = text(node, "symbol");
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String exchange = text(node, "stockExchange", "exchange");
        String exchangeShortName = text(node, "exchangeShortName");
        String type = text(node, "type");
        Boolean isEtf = bool(node, "isEtf");
        if (type == null || type.isBlank()) {
            type = isEtf != null && isEtf ? "etf" : "stock";
        }
        Boolean activelyTrading = bool(node, "isActivelyTrading");
        return new TickerSearchResultDto(
            symbol.toUpperCase(Locale.ROOT),
            text(node, "name", "companyName"),
            exchange,
            exchangeShortName,
            type,
            text(node, "country"),
            text(node, "currency"),
            activelyTrading == null || activelyTrading
        );
    }

    private static CompanyProfileDto mapProfile(JsonNode profile) {
        return new CompanyProfileDto(
            text(profile, "companyName"),
            text(profile, "description"),
            text(profile, "industry"),
            text(profile, "sector"),
            text(profile, "ceo"),
            text(profile, "website"),
            integer(profile, "fullTimeEmployees"),
            longValue(profile, "marketCap", "mktCap"),
            text(profile, "country"),
            text(profile, "city"),
            text(profile, "state"),
            text(profile, "address"),
            text(profile, "phone"),
            text(profile, "exchange", "exchangeShortName"),
            text(profile, "currency"),
            bool(profile, "isEtf"),
            bool(profile, "isActivelyTrading")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String text(JsonNode node, String primaryField, String fallbackField) {
        String value = text(node, primaryField);
        return value == null ? text(node, fallbackField) : value;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long longValue(JsonNode node, String primaryField, String fallbackField) {
        Long value = longValue(node, primaryField);
        return value == null ? longValue(node, fallbackField) : value;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }
}
