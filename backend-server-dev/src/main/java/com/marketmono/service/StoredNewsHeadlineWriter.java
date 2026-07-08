package com.marketmono.service;

import com.marketmono.dto.NewsHeadlineDto;
import com.marketmono.entity.StoredNewsHeadline;
import com.marketmono.repository.StoredNewsHeadlineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Normalizes WebSocket news headlines into the persisted read model.
 */
@Service
@Slf4j
public class StoredNewsHeadlineWriter {

    private final StoredNewsHeadlineRepository repository;
    private final Clock clock;

    @Autowired
    public StoredNewsHeadlineWriter(StoredNewsHeadlineRepository repository) {
        this(repository, Clock.systemUTC());
    }

    StoredNewsHeadlineWriter(StoredNewsHeadlineRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void saveFromWebSocket(NewsHeadlineDto dto) {
        if (dto == null || dto.headline() == null || dto.headline().isBlank()) {
            return;
        }

        String providerEventId = providerEventId(dto.meta());
        String dedupeKey = dedupeKey(dto, providerEventId);
        if (repository.existsByDedupeKey(dedupeKey)) {
            log.debug("[UW-NEWS] Skipping duplicate news headline {}", dedupeKey);
            return;
        }

        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        StoredNewsHeadline entity = new StoredNewsHeadline();
        entity.setProviderEventId(providerEventId);
        entity.setDedupeKey(dedupeKey);
        entity.setCreatedAt(dto.createdAt() == null ? now : dto.createdAt());
        entity.setHeadline(dto.headline().trim());
        entity.setSource(trimToNull(dto.source()));
        entity.setTickers(normalizedList(dto.tickers()));
        entity.setSentiment(trimToNull(dto.sentiment()));
        entity.setMajor(Boolean.TRUE.equals(dto.isMajor()));
        entity.setTags(normalizedList(dto.tags()));
        entity.setMeta(dto.meta() == null ? Map.of() : new LinkedHashMap<>(dto.meta()));
        entity.setDescription(trimToNull(dto.description()));
        entity.setUrl(trimToNull(dto.url()));
        entity.setImageUrl(trimToNull(dto.imageUrl()));
        entity.setIngestedAt(now);
        entity.setUpdatedAt(now);

        try {
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.debug("[UW-NEWS] Duplicate news headline rejected by database {}", dedupeKey);
        }
    }

    private String providerEventId(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return null;
        }
        for (String key : List.of("provider_id", "id", "news_id", "event_id")) {
            Object value = meta.get(key);
            if (value != null) {
                String normalized = trimToNull(value.toString());
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private String dedupeKey(NewsHeadlineDto dto, String providerEventId) {
        if (providerEventId != null) {
            return "uw:" + providerEventId;
        }

        String raw = normalizedText(dto.createdAt())
            + "|" + normalizedText(dto.source())
            + "|" + normalizedText(dto.headline())
            + "|" + normalizedText(dto.url())
            + "|" + normalizedTickerKey(dto.tickers());
        return "uw:hash:" + sha256Hex(raw).substring(0, 48);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private String normalizedText(Object value) {
        String text = value == null ? "" : value.toString().trim();
        return text.toLowerCase(Locale.ROOT);
    }

    private String normalizedTickerKey(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return "";
        }
        return tickers.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .sorted()
            .collect(Collectors.joining(","));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
