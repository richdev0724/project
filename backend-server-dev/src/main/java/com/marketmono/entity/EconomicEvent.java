package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Economic event entity for caching upstream market data provider economic calendar data
 */
@Entity
@Table(name = "economic_events",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_economic_events_event_time_type",
            columnNames = {"event", "event_time", "type", "reported_period"}
        )
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EconomicEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event", nullable = false, length = 500)
    private String event;
    
    @Column(name = "forecast", length = 100)
    private String forecast;
    
    @Column(name = "prev", length = 100)
    private String prev;
    
    @Column(name = "reported_period", length = 100)
    private String reportedPeriod;
    
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "economic_event_type")
    private EconomicEventType type;
    
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "priority", nullable = false, columnDefinition = "economic_event_priority")
    private EconomicEventPriority priority;
    
    @Column(name = "cache_date", nullable = false)
    private LocalDate cacheDate;
    
    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;
}
