package com.marketmono.service;

import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import com.marketmono.entity.StoredNewsHeadline;
import com.marketmono.repository.StoredNewsHeadlineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredNewsHeadlineServiceTest {

    @Mock
    private StoredNewsHeadlineRepository repository;

    @InjectMocks
    private StoredNewsHeadlineService service;

    @Test
    void shouldFilterStoredHeadlinesAndReturnNewestMatchingRows() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
            row("Market Data", "AAPL supplier update", "2026-06-07T10:00:00Z", true, List.of()),
            row("Market Data", "AAPL options volume jumps", "2026-06-07T11:00:00Z", true, List.of("AAPL")),
            row("Market Data", "Market breadth expands", "2026-06-07T12:00:00Z", false, List.of()),
            row("Newswire", "AAPL macro update", "2026-06-07T13:00:00Z", true, List.of("AAPL"))
        ));

        NewsResponseDto response = service.getHeadlines(
            "Market Data",
            "options",
            true,
            5,
            0,
            "aapl"
        );

        assertThat(response.success()).isTrue();
        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("AAPL options volume jumps");
    }

    @Test
    void shouldApplyZeroBasedPaginationAfterFiltering() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
            row("Market Data", "Newest", "2026-06-07T12:00:00Z", false, List.of()),
            row("Market Data", "Middle", "2026-06-07T11:00:00Z", false, List.of()),
            row("Market Data", "Oldest", "2026-06-07T10:00:00Z", false, List.of())
        ));

        NewsResponseDto response = service.getHeadlines(null, null, null, 1, 1, null);

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Middle");
    }

    @Test
    void shouldSupportMultipleSourcesSearchNoHitAndTickerHeadlineFallback() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
            row("Market Data", "NVDA options volume jumps", "2026-06-07T12:00:00Z", false, List.of()),
            row("Newswire", "AAPL supplier update", "2026-06-07T11:00:00Z", false, List.of("AAPL")),
            row("Blog", "NVDA rumor recap", "2026-06-07T10:00:00Z", false, List.of("NVDA"))
        ));

        NewsResponseDto tickerResponse = service.getHeadlines(
            "Market Data, Newswire",
            null,
            null,
            10,
            0,
            "nvda"
        );
        NewsResponseDto noHitResponse = service.getHeadlines(
            "Market Data, Newswire",
            "not-present",
            null,
            10,
            0,
            null
        );

        assertThat(tickerResponse.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("NVDA options volume jumps");
        assertThat(noHitResponse.data()).isEmpty();
    }

    @Test
    void shouldClampInvalidAndOversizedLimitsToConfiguredDefaultAndMaximum() {
        List<StoredNewsHeadline> rows = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            rows.add(row(
                "Market Data",
                "Headline " + i,
                "2026-06-07T12:" + String.format("%02d", i % 60) + ":00Z",
                false,
                List.of()
            ));
        }
        when(repository.findAll(any(Sort.class))).thenReturn(rows);

        NewsResponseDto invalidLimitResponse = service.getHeadlines(null, null, null, 0, 0, null);
        NewsResponseDto oversizedLimitResponse = service.getHeadlines(null, null, null, 500, 0, null);

        assertThat(invalidLimitResponse.data()).hasSize(100);
        assertThat(oversizedLimitResponse.data()).hasSize(100);
    }

    @Test
    void shouldNotFilterByMajorWhenMajorOnlyIsOmitted() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
            row("Market Data", "Major headline", "2026-06-07T12:00:00Z", true, List.of()),
            row("Market Data", "Regular headline", "2026-06-07T11:00:00Z", false, List.of())
        ));

        NewsResponseDto response = service.getHeadlines(null, null, null, 10, 0, null);

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Major headline", "Regular headline");
    }

    @Test
    void shouldReadRowsWithinSelectedEasternDateRange() {
        OffsetDateTime startInclusive = OffsetDateTime.parse("2026-06-23T04:00:00Z");
        OffsetDateTime endExclusive = OffsetDateTime.parse("2026-06-24T04:00:00Z");
        when(repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            startInclusive,
            endExclusive
        )).thenReturn(List.of(
            row("Market Data", "Selected date newest", "2026-06-23T14:00:00Z", false, List.of()),
            row("Market Data", "Selected date oldest", "2026-06-23T10:00:00Z", false, List.of())
        ));

        NewsResponseDto response = service.getHeadlines(
            null,
            null,
            null,
            10,
            0,
            null,
            LocalDate.of(2026, 6, 23)
        );

        assertThat(response.data())
            .extracting(NewsHeadlineDto::headline)
            .containsExactly("Selected date newest", "Selected date oldest");
        verify(repository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            startInclusive,
            endExclusive
        );
        verify(repository, never()).findAll(any(Sort.class));
    }

    private static StoredNewsHeadline row(
        String source,
        String headline,
        String createdAt,
        boolean major,
        List<String> tickers
    ) {
        StoredNewsHeadline row = new StoredNewsHeadline();
        row.setCreatedAt(OffsetDateTime.parse(createdAt));
        row.setHeadline(headline);
        row.setSource(source);
        row.setTickers(tickers);
        row.setSentiment("");
        row.setMajor(major);
        row.setTags(List.of());
        row.setMeta(Map.of());
        row.setDescription(headline + " description");
        row.setUrl("https://example.test/" + headline.toLowerCase().replace(' ', '-'));
        row.setImageUrl("https://example.test/image.jpg");
        return row;
    }
}
