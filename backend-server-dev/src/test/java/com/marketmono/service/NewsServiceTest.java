package com.marketmono.service;

import com.marketmono.client.NewsApiAiClient;
import com.marketmono.client.NewsApiClient;
import com.marketmono.client.MarketauxNewsClient;
import com.marketmono.client.PolygonNewsClient;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.client.FmpClient;
import com.marketmono.dto.CompanyProfileDto;
import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import com.marketmono.dto.StockInformationResponse;
import com.marketmono.dto.TopHeadlineArticleDto;
import com.marketmono.dto.TopHeadlineSourceDto;
import com.marketmono.dto.TopHeadlinesResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private NewsApiClient newsApiClient;

    @Mock
    private NewsApiAiClient newsApiAiClient;

    @Mock
    private PolygonNewsClient polygonNewsClient;

    @Mock
    private MarketauxNewsClient marketauxNewsClient;

    @Mock
    private FmpClient fmpClient;

    @Mock
    private StoredNewsHeadlineService storedNewsHeadlineService;

    @InjectMocks
    private NewsService newsService;

    @Test
    void shouldMergeDefaultHeadlinesDeduplicateByHeadlineAndSortNewestFirst() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of(
                headline("Market Data", "Rates rise", "2026-06-07T10:00:00Z"),
                headline("Market Data", "Apple rally", "2026-06-07T09:00:00Z")
            )));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenReturn(new TopHeadlinesResponseDto("ok", 2, List.of(
                article("Reuters", "Rates rise", "2026-06-07T12:00:00Z"),
                article("CNBC", "Semis rebound", "2026-06-07T11:00:00Z")
            )));
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of(
                headline("NewsAPI.ai", "Global demand", "2026-06-07T13:00:00Z")
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            "ignored-source-list", "ignored search", true, 10, 9, null, null, null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Global demand", "Semis rebound", "Rates rise", "Apple rally");
    }

    @Test
    void shouldFilterMergedHeadlinesByTickerArrayOrHeadline() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "Chip demand expands", "2026-06-07T10:00:00Z", List.of("NVDA")),
                headlineWithTickers("Market Data", "Bank trade", "2026-06-07T09:00:00Z", List.of("JPM"))
            )));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenReturn(new TopHeadlinesResponseDto("ok", 1, List.of(
                article("CNBC", "NVDA supplier outlook improves", "2026-06-07T11:00:00Z")
            )));
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("NewsAPI.ai", "Macro update", "2026-06-07T12:00:00Z", List.of())
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, "nvda", null, null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("NVDA supplier outlook improves", "Chip demand expands");
        verify(storedNewsHeadlineService).getHeadlines(null, null, null, 100, 0, null);
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldIncludeImageCapableRetiredNodeNewsProvidersInDefaultMergedFeed() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of()));
        when(polygonNewsClient.getHeadlines())
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithImage(
                    "Market Wire",
                    "Chip stocks rally",
                    "2026-06-07T14:00:00Z",
                    "https://example.test/chips",
                    "https://example.test/chips.jpg"
                )
            )));
        when(marketauxNewsClient.getHeadlines())
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithImage(
                    "Newswire",
                    "Markets climb",
                    "2026-06-07T13:00:00Z",
                    "https://example.test/markets",
                    "https://example.test/markets.jpg"
                )
            )));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenReturn(new TopHeadlinesResponseDto("ok", 0, List.of()));
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of()));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, null, null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::imageUrl)
            .containsExactly("https://example.test/chips.jpg", "https://example.test/markets.jpg");
    }

    @Test
    void shouldFilterMergedHeadlinesBeforeDeduplicatingLikeRetiredNodeRoute() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "Same title", "2026-06-07T10:00:00Z", List.of())
            )));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenReturn(new TopHeadlinesResponseDto("ok", 1, List.of(
                article("CNBC", "Same title", "2026-06-07T11:00:00Z")
            )));
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("NewsAPI.ai", "Same title", "2026-06-07T12:00:00Z", List.of("AAPL"))
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, "aapl", null, null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::source)
            .containsExactly("NewsAPI.ai");
    }

    @Test
    void shouldReturnExplicitNewsApiAiSourceOnly() {
        when(newsApiAiClient.getHeadlines("global"))
            .thenReturn(new NewsResponseDto(List.of(
                headline("NewsAPI.ai", "Central bank update", "2026-06-07T10:00:00Z")
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, "newsapiai", "global"
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::source)
            .containsExactly("NewsAPI.ai");
        verifyNoInteractions(unusualWhalesClient);
        verify(newsApiClient, never()).getTopHeadlines(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnExplicitNewsApiAiTickerMatchesFromProviderTickerInference() {
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("NewsAPI.ai", "Nvidia revenue forecast improves", "2026-06-07T10:00:00Z", List.of("NVDA")),
                headlineWithTickers("NewsAPI.ai", "Macro inflation update", "2026-06-07T09:00:00Z", List.of())
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, "nvda", "newsapiai", null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Nvidia revenue forecast improves");
    }

    @Test
    void shouldReturnExplicitNewsApiFailureWithoutMergingOtherProviders() {
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenThrow(new IllegalStateException("NewsAPI unavailable"));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, "newsapi", null
        );

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isEmpty();
        assertThat(response.error()).contains("NewsAPI unavailable");
        verifyNoInteractions(unusualWhalesClient);
        verify(newsApiAiClient, never()).getHeadlines(any());
    }

    @Test
    void shouldReturnExplicitMarketDataSourceOnlyWithRequestedUpstreamFilters() {
        when(storedNewsHeadlineService.getHeadlines("ignored", "ignored", true, 1, 7, "AAPL"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "Market breadth expands", "2026-06-07T12:00:00Z", List.of()),
                headlineWithTickers("Market Data", "AAPL options volume jumps", "2026-06-07T11:00:00Z", List.of("AAPL")),
                headlineWithTickers("Market Data", "AAPL supplier update", "2026-06-07T10:00:00Z", List.of())
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            "ignored", "ignored", true, 1, 7, "aapl", "market_data", null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("AAPL options volume jumps");
        verify(storedNewsHeadlineService).getHeadlines("ignored", "ignored", true, 1, 7, "AAPL");
        verifyNoInteractions(unusualWhalesClient);
        verify(newsApiClient, never()).getTopHeadlines(any(), any(), any(), any(), any(), any());
        verify(newsApiAiClient, never()).getHeadlines(any());
    }

    @Test
    void shouldNotCallUpstreamUwRestForExplicitMarketDataSource() {
        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, "market_data", null
        );

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEmpty();
        verify(storedNewsHeadlineService).getHeadlines(null, null, null, 10, 0, null);
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldReadStoredMarketDataNewsEachTimeWithoutBackendCache() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 10, 0, "AAPL"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "AAPL options volume jumps", "2026-06-07T11:00:00Z", List.of("AAPL"))
            )));

        NewsResponseDto first = newsService.getNewsHeadlines(
            null, null, null, 10, null, "aapl", "market_data", null
        );
        NewsResponseDto second = newsService.getNewsHeadlines(
            null, null, null, 10, null, "aapl", "market_data", null
        );

        assertThat(first.data()).hasSize(1);
        assertThat(second.data()).hasSize(1);
        verify(storedNewsHeadlineService, times(2)).getHeadlines(null, null, null, 10, 0, "AAPL");
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldPassPageAndLimitToStoredMarketDataNewsProvider() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 1, 0, "AAPL"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "AAPL page zero", "2026-06-07T11:00:00Z", List.of("AAPL"))
            )));
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 2, 1, "AAPL"))
            .thenReturn(new NewsResponseDto(List.of(
                headlineWithTickers("Market Data", "AAPL page one", "2026-06-07T10:00:00Z", List.of("AAPL"))
            )));

        NewsResponseDto first = newsService.getNewsHeadlines(
            null, null, null, 1, 0, "aapl", "market_data", null
        );
        NewsResponseDto second = newsService.getNewsHeadlines(
            null, null, null, 2, 1, "AAPL", "market_data", null
        );

        assertThat(first.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("AAPL page zero");
        assertThat(second.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("AAPL page one");
        verify(storedNewsHeadlineService).getHeadlines(null, null, null, 1, 0, "AAPL");
        verify(storedNewsHeadlineService).getHeadlines(null, null, null, 2, 1, "AAPL");
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldPassSelectedDateToStoredMarketDataNewsProvider() {
        LocalDate selectedDate = LocalDate.of(2026, 6, 23);
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null, selectedDate))
            .thenReturn(new NewsResponseDto(List.of(
                headline("Market Data", "Selected day headline", "2026-06-23T14:00:00Z")
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 100, null, null, "market_data", null, selectedDate
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Selected day headline");
        verify(storedNewsHeadlineService).getHeadlines(null, null, null, 100, 0, null, selectedDate);
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldKeepMergedHeadlinesWhenOneProviderFails() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of(
                headline("Market Data", "Options volume jumps", "2026-06-07T10:00:00Z")
            )));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenThrow(new IllegalStateException("NewsAPI unavailable"));
        when(newsApiAiClient.getHeadlines("business"))
            .thenReturn(new NewsResponseDto(List.of(
                headline("NewsAPI.ai", "Global macro update", "2026-06-07T11:00:00Z")
            )));

        NewsResponseDto response = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, null, null
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Global macro update", "Options volume jumps");
    }

    @Test
    void shouldUseRequestedTopicForDefaultMergedNewsApiAiProvider() {
        when(storedNewsHeadlineService.getHeadlines(null, null, null, 100, 0, null))
            .thenReturn(new NewsResponseDto(List.of()));
        when(newsApiClient.getTopHeadlines("us", "business", null, null, 30, null))
            .thenReturn(new TopHeadlinesResponseDto("ok", 0, List.of()));
        when(newsApiAiClient.getHeadlines("tech"))
            .thenReturn(new NewsResponseDto(List.of(
                headline("NewsAPI.ai", "Chip demand grows", "2026-06-07T10:00:00Z")
            )));

        NewsResponseDto res = newsService.getNewsHeadlines(
            null, null, null, 10, null, null, null, "tech"
        );

        assertThat(res.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Chip demand grows");
        verify(newsApiAiClient).getHeadlines("tech");
    }

    @Test
    void shouldReturnCompanyProfileFromFmpWithUppercaseTicker() {
        CompanyProfileDto profile = new CompanyProfileDto(
            "NVIDIA Corporation",
            "Accelerated computing company",
            "Semiconductors",
            "Technology",
            "Jensen Huang",
            "https://www.nvidia.com",
            29600,
            3_000_000_000_000L,
            "US",
            "Santa Clara",
            "CA",
            "2788 San Tomas Expressway",
            "408-486-2000",
            "NASDAQ",
            "USD",
            false,
            true
        );
        when(fmpClient.getCompanyProfile("NVDA")).thenReturn(profile);

        CompanyProfileDto response = newsService.getCompanyProfile("nvda");

        assertThat(response).isEqualTo(profile);
        verify(fmpClient).getCompanyProfile("NVDA");
    }

    @Test
    void shouldReturnCompanyProfileFromMarketDataWhenFmpIsUnavailable() {
        when(fmpClient.getCompanyProfile("NVDA")).thenReturn(null);
        when(unusualWhalesClient.getStockInformation("NVDA"))
            .thenReturn(new StockInformationResponse(new StockInformationResponse.StockInformationData(
                null,
                null,
                "NVIDIA Corporation",
                null,
                null,
                null,
                true,
                "Common Stock",
                "3000000000000",
                "big",
                null,
                "Technology",
                "Accelerated computing company",
                "NVDA",
                List.of("semiconductors")
            )));

        CompanyProfileDto response = newsService.getCompanyProfile("nvda");

        assertThat(response.companyName()).isEqualTo("NVIDIA Corporation");
        assertThat(response.description()).isEqualTo("Accelerated computing company");
        assertThat(response.sector()).isEqualTo("Technology");
        assertThat(response.marketCap()).isEqualTo(3_000_000_000_000L);
        assertThat(response.exchange()).isNull();
        assertThat(response.isActivelyTrading()).isTrue();
    }

    @Test
    void shouldReturnBundledTickerBioCompanyProfileWhenLiveProvidersMiss() {
        when(fmpClient.getCompanyProfile("NVDA")).thenReturn(null);
        when(unusualWhalesClient.getStockInformation("NVDA")).thenReturn(null);

        CompanyProfileDto response = newsService.getCompanyProfile("nvda");

        assertThat(response).isNotNull();
        assertThat(response.companyName()).isEqualTo("NVIDIA");
        assertThat(response.description()).contains("GPU");
        assertThat(response.sector()).isEqualTo("Mega-Cap Tech");
        assertThat(response.isActivelyTrading()).isTrue();
    }

    private static TopHeadlineArticleDto article(String source, String title, String publishedAt) {
        return new TopHeadlineArticleDto(
            new TopHeadlineSourceDto(null, source),
            null,
            title,
            title + " description",
            "https://example.test/" + title.toLowerCase().replace(' ', '-'),
            "https://example.test/image.jpg",
            publishedAt,
            null
        );
    }

    private static NewsHeadlineDto headline(String source, String headline, String createdAt) {
        return headlineWithTickers(source, headline, createdAt, List.of());
    }

    private static NewsHeadlineDto headlineWithTickers(
        String source,
        String headline,
        String createdAt,
        List<String> tickers
    ) {
        return new NewsHeadlineDto(
            OffsetDateTime.parse(createdAt),
            headline,
            source,
            tickers,
            "",
            false,
            List.of(),
            Map.of()
        );
    }

    private static NewsHeadlineDto headlineWithImage(
        String source,
        String headline,
        String createdAt,
        String url,
        String imageUrl
    ) {
        return new NewsHeadlineDto(
            OffsetDateTime.parse(createdAt),
            headline,
            source,
            List.of(),
            "",
            false,
            List.of(),
            Map.of(),
            headline + " description",
            url,
            imageUrl
        );
    }
}
