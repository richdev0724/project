package com.marketmono.service;

import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.NewsHeadlineDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class StoredNewsHeadlineIngestionServiceTest {

    private final UnusualWhalesWebSocketClient webSocketClient = mock(UnusualWhalesWebSocketClient.class);
    private final StoredNewsHeadlineWriter writer = mock(StoredNewsHeadlineWriter.class);

    @Test
    void shouldRegisterNewsHeadlineHandlerWhenWebSocketIsEnabled() {
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getWebsocket().setEnabled(true);
        StoredNewsHeadlineIngestionService service =
            new StoredNewsHeadlineIngestionService(webSocketClient, config, writer);

        service.initialize();

        verify(webSocketClient).onNewsHeadline(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSaveNewsHeadlineReceivedFromRegisteredHandler() {
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getWebsocket().setEnabled(true);
        StoredNewsHeadlineIngestionService service =
            new StoredNewsHeadlineIngestionService(webSocketClient, config, writer);

        service.initialize();

        org.mockito.ArgumentCaptor<Consumer<NewsHeadlineDto>> handlerCaptor =
            org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(webSocketClient).onNewsHeadline(handlerCaptor.capture());

        NewsHeadlineDto dto = headline();
        handlerCaptor.getValue().accept(dto);

        verify(writer).saveFromWebSocket(dto);
    }

    @Test
    void shouldNotRegisterNewsHeadlineHandlerWhenWebSocketIsDisabled() {
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getWebsocket().setEnabled(false);
        StoredNewsHeadlineIngestionService service =
            new StoredNewsHeadlineIngestionService(webSocketClient, config, writer);

        service.initialize();

        verify(webSocketClient, never()).onNewsHeadline(any());
    }

    private static NewsHeadlineDto headline() {
        return new NewsHeadlineDto(
            OffsetDateTime.parse("2026-06-24T10:15:30Z"),
            "NVDA announces new AI platform",
            "Unusual Whales",
            List.of("NVDA"),
            "positive",
            true,
            List.of("AI"),
            Map.of("provider_id", "uw-news-123"),
            "Nvidia announced a new AI platform.",
            "https://example.com/nvda",
            "https://example.com/nvda.png"
        );
    }
}
