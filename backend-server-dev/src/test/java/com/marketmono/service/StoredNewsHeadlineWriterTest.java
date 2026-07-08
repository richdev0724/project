package com.marketmono.service;

import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.entity.StoredNewsHeadline;
import com.marketmono.repository.StoredNewsHeadlineRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoredNewsHeadlineWriterTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-06-24T12:00:00Z"),
        ZoneOffset.UTC
    );

    private final StoredNewsHeadlineRepository repository = mock(StoredNewsHeadlineRepository.class);
    private final StoredNewsHeadlineWriter writer = new StoredNewsHeadlineWriter(repository, CLOCK);

    @Test
    void shouldPersistWebSocketHeadlineWithDedupeAndTimestamps() {
        when(repository.existsByDedupeKey("uw:uw-news-123")).thenReturn(false);

        writer.saveFromWebSocket(headline());

        ArgumentCaptor<StoredNewsHeadline> captor = ArgumentCaptor.forClass(StoredNewsHeadline.class);
        verify(repository).save(captor.capture());
        StoredNewsHeadline saved = captor.getValue();
        assertThat(saved.getProviderEventId()).isEqualTo("uw-news-123");
        assertThat(saved.getDedupeKey()).isEqualTo("uw:uw-news-123");
        assertThat(saved.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-06-24T10:15:30Z"));
        assertThat(saved.getHeadline()).isEqualTo("NVDA announces new AI platform");
        assertThat(saved.getSource()).isEqualTo("Unusual Whales");
        assertThat(saved.getTickers()).containsExactly("NVDA", "SMH");
        assertThat(saved.getMajor()).isTrue();
        assertThat(saved.getTags()).containsExactly("AI", "semiconductors");
        assertThat(saved.getMeta()).containsEntry("provider_id", "uw-news-123");
        assertThat(saved.getUrl()).isEqualTo("https://example.com/nvda");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/nvda.png");
        assertThat(saved.getIngestedAt()).isEqualTo(OffsetDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
        assertThat(saved.getUpdatedAt()).isEqualTo(OffsetDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
    }

    @Test
    void shouldSkipDuplicateWebSocketHeadline() {
        when(repository.existsByDedupeKey("uw:uw-news-123")).thenReturn(true);

        writer.saveFromWebSocket(headline());

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldIncludeUrlAndTickersInFallbackDedupeKey() {
        writer.saveFromWebSocket(headlineWithoutProviderId(
            "https://example.com/nvda",
            List.of("NVDA", "SMH")
        ));
        writer.saveFromWebSocket(headlineWithoutProviderId(
            "https://example.com/nvda-followup",
            List.of("NVDA", "QQQ")
        ));

        ArgumentCaptor<StoredNewsHeadline> captor = ArgumentCaptor.forClass(StoredNewsHeadline.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(StoredNewsHeadline::getDedupeKey)
            .doesNotHaveDuplicates();
    }

    @Test
    void shouldTreatDatabaseUniqueConstraintConflictAsDuplicateReplay() {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
            .when(repository).save(org.mockito.ArgumentMatchers.any(StoredNewsHeadline.class));

        writer.saveFromWebSocket(headline());

        verify(repository).save(org.mockito.ArgumentMatchers.any(StoredNewsHeadline.class));
    }

    @Test
    void shouldCreateSpringBeanWithRepositoryConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StoredNewsHeadlineRepository.class, () -> repository);
            context.register(StoredNewsHeadlineWriter.class);

            context.refresh();

            assertThat(context.getBean(StoredNewsHeadlineWriter.class)).isNotNull();
        }
    }

    private static NewsHeadlineDto headline() {
        return new NewsHeadlineDto(
            OffsetDateTime.parse("2026-06-24T10:15:30Z"),
            "NVDA announces new AI platform",
            "Unusual Whales",
            List.of("NVDA", "SMH"),
            "positive",
            true,
            List.of("AI", "semiconductors"),
            Map.of("provider_id", "uw-news-123"),
            "Nvidia announced a new AI platform.",
            "https://example.com/nvda",
            "https://example.com/nvda.png"
        );
    }

    private static NewsHeadlineDto headlineWithoutProviderId(String url, List<String> tickers) {
        return new NewsHeadlineDto(
            OffsetDateTime.parse("2026-06-24T10:15:30Z"),
            "NVDA announces new AI platform",
            "Unusual Whales",
            tickers,
            "positive",
            true,
            List.of("AI", "semiconductors"),
            Map.of(),
            "Nvidia announced a new AI platform.",
            url,
            "https://example.com/nvda.png"
        );
    }
}
