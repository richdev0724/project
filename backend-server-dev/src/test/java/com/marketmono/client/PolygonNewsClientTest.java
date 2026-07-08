package com.marketmono.client;

import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PolygonNewsClientTest {

    @Test
    void shouldUseReferenceNewsEndpointAndMapArticleImageUrl() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                requestedUri.set(request.url());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        {
                          "results": [
                            {
                              "title": "Chip stocks rally",
                              "description": "Semiconductor shares moved higher.",
                              "published_utc": "2026-06-07T14:00:00Z",
                              "article_url": "https://example.test/chips",
                              "image_url": "https://example.test/chips.jpg",
                              "publisher": { "name": "Market Wire" },
                              "tickers": ["NVDA", "AMD"],
                              "keywords": ["semiconductors"]
                            }
                          ]
                        }
                        """)
                    .build());
            })
            .build();
        PolygonNewsClient client = new PolygonNewsClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.polygon.io");

        NewsResponseDto response = client.getHeadlines();

        assertThat(requestedUri.get().getPath()).isEqualTo("/v2/reference/news");
        assertThat(requestedUri.get().getQuery()).contains("apiKey=test-key", "sort=published_utc");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(1);
        NewsHeadlineDto headline = response.data().get(0);
        assertThat(headline.source()).isEqualTo("Market Wire");
        assertThat(headline.url()).isEqualTo("https://example.test/chips");
        assertThat(headline.imageUrl()).isEqualTo("https://example.test/chips.jpg");
        assertThat(headline.tickers()).containsExactly("NVDA", "AMD");
    }
}
