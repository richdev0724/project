package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.MarketauxNewsClient;
import com.marketmono.client.NewsApiAiClient;
import com.marketmono.client.NewsApiClient;
import com.marketmono.client.FmpClient;
import com.marketmono.client.PolygonNewsClient;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.CompanyProfileDto;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import com.marketmono.dto.StockInformationResponse;
import com.marketmono.dto.TickerBioInfoDto;
import com.marketmono.dto.TopHeadlineArticleDto;
import com.marketmono.dto.TopHeadlinesResponseDto;
import com.marketmono.exception.NewsApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service for fetching provider-backed news headlines.
 * Preserves the retired frontend Node route contract for /api/v1/news/headlines.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    private static final int DEFAULT_MERGED_LIMIT = 100;
    private static final int NEWSAPI_PAGE_SIZE = 30;
    private static final String TICKER_BIO_RESOURCE = "ticker_bio_info.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<TickerBioInfoDto>> TICKER_BIO_LIST_TYPE = new TypeReference<>() {};
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final NewsApiClient newsApiClient;
    private final NewsApiAiClient newsApiAiClient;
    private final PolygonNewsClient polygonNewsClient;
    private final MarketauxNewsClient marketauxNewsClient;
    private final FmpClient fmpClient;
    private final StoredNewsHeadlineService storedNewsHeadlineService;
    private volatile Map<String, TickerBioInfoDto> tickerBioInfoByTicker;
    
    /**
     * Fetch news headlines with optional filters
     * 
     * @param sources Comma-separated news sources (e.g., "Reuters,Bloomberg")
     * @param searchTerm Search term to filter headlines
     * @param majorOnly Include only major news items
     * @param limit Number of results (default 50, max 100)
     * @param page Page number for pagination
     * @param ticker Filter by ticker symbol (optional)
     * @return News headlines response
     */
    public NewsResponseDto getNewsHeadlines(
            String sources,
            String searchTerm,
            Boolean majorOnly,
            Integer limit,
            Integer page,
            String ticker
    ) {
        return getNewsHeadlines(sources, searchTerm, majorOnly, limit, page, ticker, null, null);
    }

    public NewsResponseDto getNewsHeadlines(
            String sources,
            String searchTerm,
            Boolean majorOnly,
            Integer limit,
            Integer page,
            String ticker,
            String source,
            String topic
    ) {
        return getNewsHeadlines(sources, searchTerm, majorOnly, limit, page, ticker, source, topic, null);
    }

    public NewsResponseDto getNewsHeadlines(
            String sources,
            String searchTerm,
            Boolean majorOnly,
            Integer limit,
            Integer page,
            String ticker,
            String source,
            String topic,
            LocalDate date
    ) {
        String normalizedSource = normalizeSource(source);
        int finalLimit = normalizeLimit(limit);

        return switch (normalizedSource) {
            case "market_data" -> sliceAndFilter(
                safeUnusualWhales(
                    sources,
                    searchTerm,
                    majorOnly,
                    finalLimit,
                    normalizePage(page),
                    normalizeTicker(ticker),
                    date
                ),
                ticker,
                finalLimit
            );
            case "newsapi" -> sliceAndFilter(
                safeNewsApiHeadlines(),
                ticker,
                finalLimit
            );
            case "newsapiai" -> sliceAndFilter(safeNewsApiAi(topic), ticker, finalLimit);
            case "polygon" -> sliceAndFilter(safePolygonNews(), ticker, finalLimit);
            case "marketaux" -> sliceAndFilter(safeMarketauxNews(), ticker, finalLimit);
            default -> NewsResponseDto.success(mergeHeadlines(
                safeUnusualWhales().data(),
                safePolygonNews().data(),
                safeMarketauxNews().data(),
                safeNewsApiHeadlines().data(),
                safeNewsApiAi(topic).data(),
                ticker,
                finalLimit
            ));
        };
    }

    public CompanyProfileDto getCompanyProfile(String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return null;
        }

        CompanyProfileDto fmpProfile = fmpClient.getCompanyProfile(normalizedTicker);
        if (fmpProfile != null) {
            return fmpProfile;
        }

        CompanyProfileDto stockInformationProfile = getCompanyProfileFromStockInformation(normalizedTicker);
        if (stockInformationProfile != null) {
            return stockInformationProfile;
        }

        return getCompanyProfileFromTickerBio(normalizedTicker);
    }

    private CompanyProfileDto getCompanyProfileFromStockInformation(String ticker) {
        try {
            StockInformationResponse response = unusualWhalesClient.getStockInformation(ticker);
            if (response == null || response.data() == null) {
                return null;
            }

            StockInformationResponse.StockInformationData data = response.data();
            if (firstNonBlank(data.fullName(), data.shortDescription(), data.sector(), data.marketcap()) == null) {
                return null;
            }

            return new CompanyProfileDto(
                firstNonBlank(data.fullName(), data.symbol(), ticker),
                blankToNull(data.shortDescription()),
                null,
                blankToNull(data.sector()),
                null,
                null,
                null,
                parseMarketCap(data.marketcap()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                isEtf(data.issueType(), data.fullName(), data.sector()),
                true
            );
        } catch (Exception e) {
            log.warn("Market data stock information fallback failed for {}", ticker, e);
            return null;
        }
    }

    private CompanyProfileDto getCompanyProfileFromTickerBio(String ticker) {
        TickerBioInfoDto tickerBioInfo = tickerBioInfoByTicker().get(ticker);
        if (tickerBioInfo == null) {
            return null;
        }

        return new CompanyProfileDto(
            firstNonBlank(tickerBioInfo.name(), ticker),
            blankToNull(tickerBioInfo.bio()),
            null,
            blankToNull(tickerBioInfo.sector()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            isEtf(tickerBioInfo.name(), tickerBioInfo.sector(), tickerBioInfo.bio()),
            true
        );
    }

    private Map<String, TickerBioInfoDto> tickerBioInfoByTicker() {
        Map<String, TickerBioInfoDto> cached = tickerBioInfoByTicker;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (tickerBioInfoByTicker == null) {
                tickerBioInfoByTicker = loadTickerBioInfoByTicker();
            }
            return tickerBioInfoByTicker;
        }
    }

    private Map<String, TickerBioInfoDto> loadTickerBioInfoByTicker() {
        try (InputStream inputStream = new ClassPathResource(TICKER_BIO_RESOURCE).getInputStream()) {
            List<TickerBioInfoDto> tickerBioInfo = OBJECT_MAPPER.readValue(inputStream, TICKER_BIO_LIST_TYPE);
            Map<String, TickerBioInfoDto> byTicker = new LinkedHashMap<>();
            if (tickerBioInfo == null) {
                return Map.of();
            }
            for (TickerBioInfoDto item : tickerBioInfo) {
                if (item == null) {
                    continue;
                }
                String normalizedTicker = normalizeTicker(item.ticker());
                if (normalizedTicker != null) {
                    byTicker.put(normalizedTicker, item);
                }
            }
            return Map.copyOf(byTicker);
        } catch (IOException e) {
            log.warn("Failed to load bundled ticker bio information from {}", TICKER_BIO_RESOURCE, e);
            return Map.of();
        }
    }

    private NewsResponseDto safeUnusualWhales() {
        return safeUnusualWhales(null, null, null, DEFAULT_MERGED_LIMIT, 0, null);
    }

    private NewsResponseDto safeUnusualWhales(
        String sources,
        String searchTerm,
        Boolean majorOnly,
        int limit,
        int page,
        String ticker
    ) {
        return safeUnusualWhales(sources, searchTerm, majorOnly, limit, page, ticker, null);
    }

    private NewsResponseDto safeUnusualWhales(
        String sources,
        String searchTerm,
        Boolean majorOnly,
        int limit,
        int page,
        String ticker,
        LocalDate date
    ) {
        try {
            NewsResponseDto response = date == null
                ? storedNewsHeadlineService.getHeadlines(
                    sources,
                    searchTerm,
                    majorOnly,
                    limit,
                    page,
                    ticker
                )
                : storedNewsHeadlineService.getHeadlines(
                    sources,
                    searchTerm,
                    majorOnly,
                    limit,
                    page,
                    ticker,
                    date
                );
            List<NewsHeadlineDto> data = response != null && response.data() != null
                ? response.data()
                : List.of();
            boolean success = response == null
                || response.success() == null
                || Boolean.TRUE.equals(response.success());
            return new NewsResponseDto(data, success, response == null ? null : response.error());
        } catch (Exception e) {
            log.warn("Stored market data news provider failed", e);
            return NewsResponseDto.failure(e.getMessage());
        }
    }

    private NewsResponseDto safeNewsApiHeadlines() {
        try {
            TopHeadlinesResponseDto response = newsApiClient.getTopHeadlines(
                "us", "business", null, null, NEWSAPI_PAGE_SIZE, null
            );
            if (response == null || response.articles() == null) {
                return NewsResponseDto.failure("No NewsAPI.org response");
            }
            List<NewsHeadlineDto> data = response.articles().stream()
                .filter(article -> article.title() != null && !article.title().isBlank())
                .filter(article -> article.source() != null
                    && article.source().name() != null
                    && !article.source().name().isBlank())
                .map(NewsService::mapNewsApiArticle)
                .toList();
            return NewsResponseDto.success(data);
        } catch (NewsApiRequestException e) {
            if (e.getMessage() != null && e.getMessage().contains("API key is not configured")) {
                log.debug("NewsAPI.org headlines provider skipped: {}", e.getMessage());
            } else {
                log.warn("NewsAPI.org headlines provider failed", e);
            }
            return NewsResponseDto.failure(e.getMessage());
        } catch (Exception e) {
            log.warn("NewsAPI.org headlines provider failed", e);
            return NewsResponseDto.failure(e.getMessage());
        }
    }

    private NewsResponseDto safeNewsApiAi(String topic) {
        NewsResponseDto response = newsApiAiClient.getHeadlines(topic == null ? "business" : topic);
        return response == null ? NewsResponseDto.failure("No NewsAPI.ai response") : response;
    }

    private NewsResponseDto safePolygonNews() {
        NewsResponseDto response = polygonNewsClient.getHeadlines();
        return response == null ? NewsResponseDto.failure("No Polygon news response") : response;
    }

    private NewsResponseDto safeMarketauxNews() {
        NewsResponseDto response = marketauxNewsClient.getHeadlines();
        return response == null ? NewsResponseDto.failure("No Marketaux news response") : response;
    }

    private static NewsHeadlineDto mapNewsApiArticle(TopHeadlineArticleDto article) {
        return new NewsHeadlineDto(
            parseOffsetDateTime(article.publishedAt()),
            article.title(),
            article.source().name(),
            List.of(),
            "",
            false,
            List.of(),
            Map.of(),
            nullToEmpty(article.description()),
            nullToEmpty(article.url()),
            nullToEmpty(article.urlToImage())
        );
    }

    private static List<NewsHeadlineDto> mergeHeadlines(
        List<NewsHeadlineDto> unusualWhales,
        List<NewsHeadlineDto> polygon,
        List<NewsHeadlineDto> marketaux,
        List<NewsHeadlineDto> newsApi,
        List<NewsHeadlineDto> newsApiAi,
        String ticker,
        int limit
    ) {
        List<NewsHeadlineDto> providerOrdered = new ArrayList<>();
        providerOrdered.addAll(safeList(unusualWhales));
        providerOrdered.addAll(safeList(polygon));
        providerOrdered.addAll(safeList(marketaux));
        providerOrdered.addAll(safeList(newsApi));
        providerOrdered.addAll(safeList(newsApiAi));

        Set<String> seenTitles = new LinkedHashSet<>();
        List<NewsHeadlineDto> merged = new ArrayList<>();
        appendDeduped(merged, seenTitles, applyTickerFilter(providerOrdered, ticker));

        return merged.stream()
            .sorted(Comparator.comparing(
                NewsHeadlineDto::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .limit(limit)
            .toList();
    }

    private static void appendDeduped(
        List<NewsHeadlineDto> target,
        Set<String> seenTitles,
        List<NewsHeadlineDto> source
    ) {
        for (NewsHeadlineDto item : source) {
            String key = item.headline() == null ? "" : item.headline().toLowerCase(Locale.ROOT).trim();
            if (!key.isBlank() && seenTitles.add(key)) {
                target.add(item);
            }
        }
    }

    private static NewsResponseDto sliceAndFilter(NewsResponseDto response, String ticker, int limit) {
        List<NewsHeadlineDto> data = response == null ? List.of() : response.data();
        return new NewsResponseDto(
            sliceAndFilter(data, ticker, limit),
            response != null && Boolean.TRUE.equals(response.success()),
            response == null ? "No response" : response.error()
        );
    }

    private static List<NewsHeadlineDto> sliceAndFilter(List<NewsHeadlineDto> data, String ticker, int limit) {
        return applyTickerFilter(safeList(data), ticker).stream()
            .limit(limit)
            .toList();
    }

    private static List<NewsHeadlineDto> applyTickerFilter(List<NewsHeadlineDto> data, String ticker) {
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            return data;
        }

        Pattern tickerPattern = Pattern.compile("\\b" + Pattern.quote(normalizedTicker) + "\\b", Pattern.CASE_INSENSITIVE);
        return data.stream()
            .filter(item -> hasTicker(item, normalizedTicker) || tickerPattern.matcher(nullToEmpty(item.headline())).find())
            .toList();
    }

    private static boolean hasTicker(NewsHeadlineDto item, String ticker) {
        return item.tickers() != null
            && item.tickers().stream().anyMatch(value -> ticker.equalsIgnoreCase(value));
    }

    private static List<NewsHeadlineDto> safeList(List<NewsHeadlineDto> data) {
        return data == null ? List.of() : data;
    }

    private static String normalizeSource(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT).trim();
    }

    private static String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return ticker.toUpperCase(Locale.ROOT).trim();
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_MERGED_LIMIT;
        }
        return Math.min(limit, DEFAULT_MERGED_LIMIT);
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static Long parseMarketCap(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return new BigDecimal(normalized.replace(",", "")).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isEtf(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                String lowerCaseValue = normalized.toLowerCase(Locale.ROOT);
                if (lowerCaseValue.contains("etf") || lowerCaseValue.contains("exchange traded fund")) {
                    return true;
                }
            }
        }
        return false;
    }

}
