package com.marketmono.entity.support;

import com.marketmono.entity.auth.User;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_tickets", indexes = {
    @Index(name = "idx_support_tickets_user_uuid", columnList = "user_uuid"),
    @Index(name = "idx_support_tickets_status", columnList = "status"),
    @Index(name = "idx_support_tickets_priority", columnList = "priority"),
    @Index(name = "idx_support_tickets_updated_at", columnList = "updated_at"),
    @Index(name = "idx_support_tickets_assigned_admin_id", columnList = "assigned_admin_id"),
    @Index(name = "idx_support_tickets_unread_staff", columnList = "user_uuid, unread_staff_message_count")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "user_uuid", nullable = false)
    private UUID userUuid;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(nullable = false, length = 160)
    private String subject;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "support_ticket_status")
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "support_ticket_priority")
    private SupportTicketPriority priority = SupportTicketPriority.MEDIUM;

    @Column(name = "assigned_admin_id")
    private UUID assignedAdminId;

    @Column(name = "unread_staff_message_count", nullable = false)
    private int unreadStaffMessageCount;

    @Column(name = "last_user_reply_at")
    private LocalDateTime lastUserReplyAt;

    @Column(name = "last_staff_reply_at")
    private LocalDateTime lastStaffReplyAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public void markUserReply(LocalDateTime now) {
        status = SupportTicketStatus.OPEN;
        lastUserReplyAt = now;
        updatedAt = now;
    }

    public void markStaffReply(LocalDateTime now) {
        status = SupportTicketStatus.PENDING_RESPONSE;
        lastStaffReplyAt = now;
        unreadStaffMessageCount++;
        updatedAt = now;
    }

    public void markRead(LocalDateTime now) {
        unreadStaffMessageCount = 0;
        updatedAt = now;
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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
