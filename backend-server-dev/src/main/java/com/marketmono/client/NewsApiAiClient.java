package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiAiClient {

    private static final String DEFAULT_TOPIC = "business";
    private static final Map<String, List<String>> TOPIC_CATEGORIES = Map.of(
        "business", List.of("dmoz/Business"),
        "global", List.of("dmoz/World", "dmoz/Society"),
        "tech", List.of("dmoz/Computers"),
        "science", List.of("dmoz/Science"),
        "health", List.of("dmoz/Health"),
        "sports", List.of("dmoz/Sports"),
        "entertainment", List.of("dmoz/Arts/Entertainment")
    );
    private static final List<String> MARKET_MOVING_TERMS = List.of(
        "fed", "federal reserve", "ecb", "interest rate", "inflation", "cpi", "ppi",
        "jobs report", "nonfarm payroll", "gdp", "recession", "guidance", "earnings",
        "revenue", "forecast", "downgrade", "upgrade", "tariff", "sanctions", "opec",
        "crude", "oil", "treasury", "bond yield", "ceasefire", "war"
    );
    private static final Set<String> TRACKED_TICKERS = Set.of(
        "AAPL", "NVDA", "TSLA", "MSFT", "AMZN", "GOOG", "GOOGL", "META", "NFLX",
        "AMD", "INTC", "MU", "AVGO", "QCOM", "PLTR", "SMCI", "ARM", "ADBE", "CRM",
        "SPY", "QQQ", "IWM", "DIA", "VIX", "GLD", "SLV", "USO", "BTC", "ETH",
        "XLF", "XLK", "XLE", "XLV", "XLI", "XLY", "XLP", "XLU", "XLC"
    );
    private static final Map<String, String> COMPANY_TO_TICKER = Map.ofEntries(
        Map.entry("apple", "AAPL"),
        Map.entry("nvidia", "NVDA"),
        Map.entry("tesla", "TSLA"),
        Map.entry("microsoft", "MSFT"),
        Map.entry("amazon", "AMZN"),
        Map.entry("alphabet", "GOOGL"),
        Map.entry("google", "GOOGL"),
        Map.entry("meta", "META"),
        Map.entry("netflix", "NFLX"),
        Map.entry("broadcom", "AVGO"),
        Map.entry("qualcomm", "QCOM"),
        Map.entry("palantir", "PLTR"),
        Map.entry("intel", "INTC"),
        Map.entry("amd", "AMD")
    );
    private static final Pattern DOLLAR_TICKER_PATTERN = Pattern.compile("\\$[A-Z]{1,5}\\b");
    private static final Pattern UPPER_TICKER_PATTERN = Pattern.compile("\\b[A-Z]{2,5}\\b");

    private final WebClient webClient;

    @Value("${newsapi-ai.api-key:}")
    private String apiKey;

    @Value("${newsapi-ai.base-url:https://eventregistry.org}")
    private String baseUrl;

    public NewsResponseDto getHeadlines(String topic) {
        if (apiKey == null || apiKey.isBlank()) {
            return NewsResponseDto.failure("API key not configured");
        }

        String normalizedTopic = normalizeTopic(topic);
        try {
            JsonNode body = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .scheme("https")
                        .host(host())
                        .path("/api/v1/article/getArticles")
                        .queryParam("apiKey", apiKey)
                        .queryParam("resultType", "articles")
                        .queryParam("articlesPage", 1)
                        .queryParam("articlesCount", 40)
                        .queryParam("articlesSortBy", "date")
                        .queryParam("includeArticleBasicInfo", true)
                        .queryParam("includeArticleBody", true)
                        .queryParam("includeArticleImage", true)
                        .queryParam("includeArticleCategories", true)
                        .queryParam("includeArticleConcepts", true)
                        .queryParam("articleBodyLen", 400)
                        .queryParam("startSourceRankPercentile", 0)
                        .queryParam("endSourceRankPercentile", 40);

                    for (String category : TOPIC_CATEGORIES.get(normalizedTopic)) {
                        builder.queryParam("categoryUri", category);
                    }
                    if ("global".equals(normalizedTopic)) {
                        builder.queryParam("categoryOper", "or");
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            return NewsResponseDto.success(mapArticles(body, normalizedTopic));
        } catch (Exception e) {
            log.warn("Failed to fetch NewsAPI.ai headlines for topic {}", normalizedTopic, e);
            return NewsResponseDto.failure(e.getMessage());
        }
    }

    private String host() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    private static String normalizeTopic(String topic) {
        String normalized = topic == null ? DEFAULT_TOPIC : topic.toLowerCase().trim();
        return TOPIC_CATEGORIES.containsKey(normalized) ? normalized : DEFAULT_TOPIC;
    }

    static List<NewsHeadlineDto> mapArticles(JsonNode body, String topic) {
        JsonNode results = body == null ? null : body.path("articles").path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<NewsHeadlineDto> headlines = new ArrayList<>();
        for (JsonNode article : results) {
            String title = text(article, "title");
            if (title.isBlank()) {
                continue;
            }
            if (!isMarketMoving(article, topic)) {
                continue;
            }
            headlines.add(new NewsHeadlineDto(
                parsePublishedAt(article),
                title,
                sourceTitle(article),
                extractTickers(article),
                "",
                false,
                inferMonoTags(article, topic),
                Map.of("erTopic", topic),
                text(article, "body"),
                text(article, "url"),
                text(article, "image")
            ));
        }
        return headlines;
    }

    static OffsetDateTime parsePublishedAt(JsonNode article) {
        String dateTime = text(article, "dateTime");
        if (dateTime.isBlank()) {
            String date = text(article, "date");
            String time = text(article, "time");
            dateTime = !date.isBlank() && !time.isBlank() ? date + "T" + time : date;
        }
        try {
            if (dateTime.isBlank()) {
                return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
            }
            if (dateTime.contains("Z") || dateTime.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(dateTime);
            }
            if (dateTime.contains("T")) {
                return LocalDateTime.parse(dateTime).atOffset(ZoneOffset.UTC);
            }
            return LocalDate.parse(dateTime).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private static List<String> inferMonoTags(JsonNode article, String topic) {
        List<String> tags = new ArrayList<>();
        tags.add("topic:" + topic);
        JsonNode categories = article.path("categories").isMissingNode()
            ? article.path("category")
            : article.path("categories");
        if (categories.isArray()) {
            int count = 0;
            for (JsonNode category : categories) {
                String label = category.isTextual()
                    ? category.asText()
                    : firstNonBlank(text(category, "label"), text(category, "uri"));
                if (!label.isBlank()) {
                    tags.add(label);
                    count++;
                }
                if (count >= 5) {
                    break;
                }
            }
        }
        return List.copyOf(tags);
    }

    private static boolean isMarketMoving(JsonNode article, String topic) {
        if (!"business".equals(topic) && !"global".equals(topic)) {
            return true;
        }

        String haystack = (
            text(article, "title") + " " +
            text(article, "body") + " " +
            categoryText(article)
        ).toLowerCase(Locale.ROOT);
        return MARKET_MOVING_TERMS.stream().anyMatch(haystack::contains);
    }

    private static String categoryText(JsonNode article) {
        JsonNode categories = article.path("categories").isMissingNode()
            ? article.path("category")
            : article.path("categories");
        if (!categories.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode category : categories) {
            if (category.isTextual()) {
                builder.append(category.asText()).append(' ');
            } else {
                builder
                    .append(text(category, "label"))
                    .append(' ')
                    .append(text(category, "uri"))
                    .append(' ');
            }
        }
        return builder.toString();
    }

    private static List<String> extractTickers(JsonNode article) {
        Set<String> found = new LinkedHashSet<>();
        String title = text(article, "title");
        String text = (
            title + " " +
            text(article, "body") + " " +
            conceptsText(article)
        ).toLowerCase(Locale.ROOT);

        COMPANY_TO_TICKER.forEach((name, ticker) -> {
            if (text.contains(name)) {
                found.add(ticker);
            }
        });

        var dollarMatcher = DOLLAR_TICKER_PATTERN.matcher(title);
        while (dollarMatcher.find()) {
            String ticker = dollarMatcher.group().substring(1);
            if (TRACKED_TICKERS.contains(ticker)) {
                found.add(ticker);
            }
        }

        var upperMatcher = UPPER_TICKER_PATTERN.matcher(title);
        while (upperMatcher.find()) {
            String ticker = upperMatcher.group();
            if (TRACKED_TICKERS.contains(ticker)) {
                found.add(ticker);
            }
        }

        return List.copyOf(found);
    }

    private static String conceptsText(JsonNode article) {
        JsonNode concepts = article.path("concepts");
        if (!concepts.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode concept : concepts) {
            builder.append(text(concept, "label")).append(' ');
        }
        return builder.toString();
    }

    private static String sourceTitle(JsonNode article) {
        return firstNonBlank(
            text(article.path("source"), "title"),
            text(article.path("src"), "title"),
            "NewsAPI.ai"
        );
    }

    private static String text(JsonNode node, String field) {
        return text(node, field, "");
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
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
