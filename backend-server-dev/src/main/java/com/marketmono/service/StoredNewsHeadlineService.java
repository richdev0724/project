package com.marketmono.service;

import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.dto.NewsResponseDto;
import com.marketmono.entity.StoredNewsHeadline;
import com.marketmono.repository.StoredNewsHeadlineRepository;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads persisted market-data news headlines populated by the UW News WebSocket pipeline.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StoredNewsHeadlineService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 100;

    private final StoredNewsHeadlineRepository repository;

    @Transactional(readOnly = true)
    public NewsResponseDto getHeadlines(
        String sources,
        String searchTerm,
        Boolean majorOnly,
        int limit,
        int page,
        String ticker
    ) {
        return getHeadlines(sources, searchTerm, majorOnly, limit, page, ticker, null);
    }

    @Transactional(readOnly = true)
    public NewsResponseDto getHeadlines(
        String sources,
        String searchTerm,
        Boolean majorOnly,
        int limit,
        int page,
        String ticker,
        LocalDate date
    ) {
        log.debug(
            "Stored news feed read requested: sources={}, searchTerm={}, majorOnly={}, limit={}, page={}, ticker={}, date={}",
            sources,
            searchTerm,
            majorOnly,
            limit,
            page,
            ticker,
            date
        );
        int finalLimit = normalizeLimit(limit);
        int finalPage = Math.max(page, 0);
        long offset = (long) finalPage * finalLimit;
        List<NewsHeadlineDto> rows = findRows(date).stream()
            .sorted(Comparator.comparing(
                StoredNewsHeadline::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .filter(row -> matchesSources(row, sources))
            .filter(row -> matchesSearch(row, searchTerm))
            .filter(row -> matchesMajor(row, majorOnly))
            .filter(row -> matchesTicker(row, ticker))
            .skip(offset)
            .limit(finalLimit)
            .map(StoredNewsHeadlineService::toDto)
            .toList();

        return NewsResponseDto.success(rows);
    }

    private List<StoredNewsHeadline> findRows(LocalDate date) {
        if (date == null) {
            return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        OffsetDateTime startInclusive = OffsetDateTime.ofInstant(DateUtils.startOfEasternDay(date), ZoneOffset.UTC);
        OffsetDateTime endExclusive = OffsetDateTime.ofInstant(DateUtils.startOfNextEasternDay(date), ZoneOffset.UTC);
        return repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            startInclusive,
            endExclusive
        );
    }

    private static NewsHeadlineDto toDto(StoredNewsHeadline row) {
        return new NewsHeadlineDto(
            row.getCreatedAt() == null ? OffsetDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC) : row.getCreatedAt(),
            nullToEmpty(row.getHeadline()),
            nullToEmpty(row.getSource()),
            safeList(row.getTickers()),
            nullToEmpty(row.getSentiment()),
            Boolean.TRUE.equals(row.getMajor()),
            safeList(row.getTags()),
            row.getMeta() == null ? Map.of() : row.getMeta(),
            row.getDescription(),
            row.getUrl(),
            row.getImageUrl()
        );
    }

    private static boolean matchesSources(StoredNewsHeadline row, String sources) {
        String normalizedSources = blankToNull(sources);
        if (normalizedSources == null) {
            return true;
        }
        Set<String> allowed = java.util.Arrays.stream(normalizedSources.split(","))
            .map(StoredNewsHeadlineService::blankToNull)
            .filter(value -> value != null)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        return allowed.isEmpty() || allowed.contains(nullToEmpty(row.getSource()).toLowerCase(Locale.ROOT));
    }

    private static boolean matchesSearch(StoredNewsHeadline row, String searchTerm) {
        String normalizedSearch = blankToNull(searchTerm);
        if (normalizedSearch == null) {
            return true;
        }
        String needle = normalizedSearch.toLowerCase(Locale.ROOT);
        return nullToEmpty(row.getHeadline()).toLowerCase(Locale.ROOT).contains(needle)
            || nullToEmpty(row.getDescription()).toLowerCase(Locale.ROOT).contains(needle)
            || containsIgnoreCase(row.getTags(), needle)
            || containsIgnoreCase(row.getTickers(), needle);
    }

    private static boolean matchesMajor(StoredNewsHeadline row, Boolean majorOnly) {
        return !Boolean.TRUE.equals(majorOnly) || Boolean.TRUE.equals(row.getMajor());
    }

    private static boolean matchesTicker(StoredNewsHeadline row, String ticker) {
        String normalizedTicker = blankToNull(ticker);
        if (normalizedTicker == null) {
            return true;
        }
        String upperTicker = normalizedTicker.toUpperCase(Locale.ROOT);
        if (row.getTickers() != null && row.getTickers().stream().anyMatch(value -> upperTicker.equalsIgnoreCase(value))) {
            return true;
        }
        Pattern tickerPattern = Pattern.compile("\\b" + Pattern.quote(upperTicker) + "\\b", Pattern.CASE_INSENSITIVE);
        return tickerPattern.matcher(nullToEmpty(row.getHeadline())).find();
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        return values != null && values.stream()
            .filter(value -> value != null)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(needle));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
