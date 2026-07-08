package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.NewsHeadlineDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsApiAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldInferTickersAndFilterBusinessArticlesToMarketMovingItems() throws Exception {
        JsonNode body = objectMapper.readTree("""
            {
              "articles": {
                "results": [
                  {
                    "title": "Nvidia revenue forecast lifts AI stocks",
                    "body": "Nvidia guidance improved after demand accelerated.",
                    "date": "2026-06-07",
                    "time": "10:15:30",
                    "source": { "title": "Event Registry Wire" },
                    "categories": [{ "label": "Business" }],
                    "concepts": [{ "label": "Nvidia" }],
                    "url": "https://example.test/nvidia",
                    "image": "https://example.test/nvidia.jpg"
                  },
                  {
                    "title": "Local festival opens downtown",
                    "body": "Artists and restaurants participated.",
                    "dateTime": "2026-06-07T11:00:00Z",
                    "source": { "title": "Local Wire" }
                  }
                ]
              }
            }
            """);

        List<NewsHeadlineDto> headlines = NewsApiAiClient.mapArticles(body, "business");

        assertThat(headlines)
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Nvidia revenue forecast lifts AI stocks");
        assertThat(headlines.get(0).tickers()).containsExactly("NVDA");
        assertThat(headlines.get(0).tags()).contains("topic:business", "Business");
        assertThat(headlines.get(0).source()).isEqualTo("Event Registry Wire");
        assertThat(headlines.get(0).createdAt())
            .isEqualTo(OffsetDateTime.of(2026, 6, 7, 10, 15, 30, 0, ZoneOffset.UTC));
    }

    @Test
    void shouldInferTrackedDollarAndUppercaseTickersFromTitle() throws Exception {
        JsonNode body = objectMapper.readTree("""
            {
              "articles": {
                "results": [
                  {
                    "title": "$AAPL and MSFT get analyst upgrade",
                    "body": "Technology shares moved higher.",
                    "dateTime": "2026-06-07T12:00:00Z",
                    "source": { "title": "Market Wire" }
                  }
                ]
              }
            }
            """);

        List<NewsHeadlineDto> headlines = NewsApiAiClient.mapArticles(body, "business");

        assertThat(headlines).hasSize(1);
        assertThat(headlines.get(0).tickers()).containsExactly("AAPL", "MSFT");
    }
}
