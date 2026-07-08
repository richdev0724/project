package com.marketmono.client;

import com.marketmono.exception.NewsApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NewsApiClientTest {

    @Test
    void shouldFailBeforeHttpWhenApiKeyIsMissing() {
        WebClient webClient = mock(WebClient.class);
        NewsApiClient client = new NewsApiClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.getTopHeadlines("us", "business", null, null, 30, null))
            .isInstanceOf(NewsApiRequestException.class)
            .hasMessageContaining("API key is not configured");

        verifyNoInteractions(webClient);
    }
}
