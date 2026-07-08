package com.marketmono.entity.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_ticket_notification_events", indexes = {
    @Index(name = "idx_support_ticket_notification_due", columnList = "status, next_attempt_at, created_at"),
    @Index(name = "idx_support_ticket_notification_ticket", columnList = "ticket_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketNotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private SupportTicketMessage message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", nullable = false, columnDefinition = "support_ticket_notification_event_type")
    private SupportTicketNotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "recipient_type", nullable = false, columnDefinition = "support_ticket_notification_recipient_type")
    private SupportTicketNotificationRecipientType recipientType;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "support_ticket_notification_status")
    private SupportTicketNotificationStatus status = SupportTicketNotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "last_error", length = 160)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static SupportTicketNotificationEvent pending(
            SupportTicket ticket,
            SupportTicketMessage message,
            SupportTicketNotificationEventType eventType,
            String recipientEmail,
            LocalDateTime now) {
        SupportTicketNotificationEvent event = new SupportTicketNotificationEvent();
        event.setTicket(ticket);
        event.setMessage(message);
        event.setEventType(eventType);
        event.setRecipientType(resolveRecipientType(eventType));
        event.setRecipientEmail(recipientEmail);
        event.setStatus(SupportTicketNotificationStatus.PENDING);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    public void markSent(LocalDateTime now) {
        status = SupportTicketNotificationStatus.SENT;
        sentAt = now;
        updatedAt = now;
        lastError = null;
    }

    public void markClaimed(LocalDateTime now) {
        status = SupportTicketNotificationStatus.IN_PROGRESS;
        updatedAt = now;
        lastError = null;
    }

    public void markFailed(Throwable throwable, LocalDateTime now, int maxAttempts) {
        attempts++;
        updatedAt = now;
        lastError = throwable.getClass().getSimpleName();
        if (attempts >= maxAttempts) {
            status = SupportTicketNotificationStatus.FAILED;
        } else {
            status = SupportTicketNotificationStatus.PENDING;
            nextAttemptAt = now.plusMinutes(Math.max(1, attempts));
        }
    }

    private static SupportTicketNotificationRecipientType resolveRecipientType(
            SupportTicketNotificationEventType eventType) {
        return eventType == SupportTicketNotificationEventType.STAFF_REPLY
            ? SupportTicketNotificationRecipientType.CUSTOMER
            : SupportTicketNotificationRecipientType.SUPPORT_TEAM;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
