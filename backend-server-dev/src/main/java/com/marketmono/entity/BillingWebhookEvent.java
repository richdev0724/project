package com.marketmono.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "billing_webhook_events",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_billing_webhook_events_provider_event",
            columnNames = {"billing_provider", "external_event_id"}
        )
    },
    indexes = {
        @Index(name = "idx_billing_webhook_events_provider", columnList = "billing_provider"),
        @Index(name = "idx_billing_webhook_events_event_type", columnList = "event_type"),
        @Index(name = "idx_billing_webhook_events_received_at", columnList = "received_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class BillingWebhookEvent {

    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_provider", nullable = false, length = 20)
    private BillingProvider billingProvider;

    @Column(name = "external_event_id", nullable = false, length = 255)
    private String externalEventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "processing_status", nullable = false, length = 20)
    @Builder.Default
    private String processingStatus = STATUS_RECEIVED;

    @Column(name = "payload_sha256", length = 64)
    private String payloadSha256;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isFailed() {
        return STATUS_FAILED.equals(processingStatus);
    }

    public void markProcessed() {
        this.processingStatus = STATUS_PROCESSED;
        this.errorMessage = null;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.processingStatus = STATUS_FAILED;
        this.errorMessage = message == null || message.length() <= 500
            ? message
            : message.substring(0, 500);
    }

    public void markNeedsReview(String message) {
        this.processingStatus = STATUS_NEEDS_REVIEW;
        this.errorMessage = message == null || message.length() <= 500
            ? message
            : message.substring(0, 500);
        this.processedAt = LocalDateTime.now();
    }
}
