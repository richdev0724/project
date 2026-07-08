package com.marketmono.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "uw_news_headlines", indexes = {
    @Index(name = "idx_uw_news_headlines_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_uw_news_headlines_source_created_at", columnList = "source, created_at DESC"),
    @Index(name = "idx_uw_news_headlines_is_major_created_at", columnList = "is_major, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class StoredNewsHeadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_event_id", length = 128)
    private String providerEventId;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 256)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false, columnDefinition = "text")
    private String headline;

    @Column(length = 128)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tickers = List.of();

    @Column(length = 64)
    private String sentiment;

    @Column(name = "is_major", nullable = false)
    private Boolean major = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> meta = Map.of();

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String url;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
