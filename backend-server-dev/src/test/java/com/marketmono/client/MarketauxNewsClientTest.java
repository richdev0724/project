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

class MarketauxNewsClientTest {

    @Test
    void shouldUseNewsAllEndpointAndMapArticleImageUrl() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                requestedUri.set(request.url());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        {
                          "data": [
                            {
                              "title": "Markets climb into close",
                              "snippet": "Indexes finished higher.",
                              "published_at": "2026-06-07T15:30:00Z",
                              "url": "https://example.test/markets",
                              "image_url": "https://example.test/markets.jpg",
                              "source": "Newswire",
                              "keywords": ["markets"],
                              "entities": [{ "symbol": "SPY" }]
                            }
                          ]
                        }
                        """)
                    .build());
            })
            .build();
        MarketauxNewsClient client = new MarketauxNewsClient(webClient);
        ReflectionTestUtils.setField(client, "apiToken", "test-token");
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.marketaux.com");

        NewsResponseDto response = client.getHeadlines();

        assertThat(requestedUri.get().getPath()).isEqualTo("/v1/news/all");
        assertThat(requestedUri.get().getQuery()).contains("api_token=test-token", "categories=business,finance,markets");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(1);
        NewsHeadlineDto headline = response.data().get(0);
        assertThat(headline.source()).isEqualTo("Newswire");
        assertThat(headline.url()).isEqualTo("https://example.test/markets");
        assertThat(headline.imageUrl()).isEqualTo("https://example.test/markets.jpg");
        assertThat(headline.tickers()).containsExactly("SPY");
    }
}
