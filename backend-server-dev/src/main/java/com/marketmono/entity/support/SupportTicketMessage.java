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
@Table(name = "support_ticket_messages", indexes = {
    @Index(name = "idx_support_ticket_messages_ticket_created", columnList = "ticket_id, created_at"),
    @Index(name = "idx_support_ticket_messages_unread_staff", columnList = "ticket_id, author_type, read_by_user_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "author_type", nullable = false, columnDefinition = "support_ticket_message_author_type")
    private SupportTicketMessageAuthorType authorType;

    @Column(name = "author_user_uuid")
    private UUID authorUserUuid;

    @Column(name = "author_admin_id")
    private UUID authorAdminId;

    @Column(name = "author_display_name", length = 160)
    private String authorDisplayName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "read_by_user_at")
    private LocalDateTime readByUserAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static SupportTicketMessage userReply(
            SupportTicket ticket,
            UUID authorUserUuid,
            String body,
            LocalDateTime createdAt) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthorType(SupportTicketMessageAuthorType.USER);
        message.setAuthorUserUuid(authorUserUuid);
        message.setAuthorDisplayName("Customer");
        message.setBody(body);
        message.setCreatedAt(createdAt);
        return message;
    }

    public static SupportTicketMessage staffReply(
            SupportTicket ticket,
            UUID authorAdminId,
            String authorDisplayName,
            String body,
            LocalDateTime createdAt) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthorType(SupportTicketMessageAuthorType.STAFF);
        message.setAuthorAdminId(authorAdminId);
        message.setAuthorDisplayName(authorDisplayName);
        message.setBody(body);
        message.setCreatedAt(createdAt);
        return message;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
