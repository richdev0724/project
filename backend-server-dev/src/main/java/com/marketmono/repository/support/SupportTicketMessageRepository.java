package com.marketmono.repository.support;

import com.marketmono.entity.support.SupportTicketMessage;
import com.marketmono.entity.support.SupportTicketMessageAuthorType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {

    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    long countByTicketIdAndAuthorTypeAndReadByUserAtIsNull(
        UUID ticketId,
        SupportTicketMessageAuthorType authorType
    );

    long countByAuthorTypeAndCreatedAtGreaterThanEqual(
        SupportTicketMessageAuthorType authorType,
        LocalDateTime createdAt
    );

    @Modifying
    @Query(value = """
        UPDATE support_ticket_messages
        SET read_by_user_at = :readAt
        WHERE ticket_id = :ticketId
          AND author_type = CAST('STAFF' AS support_ticket_message_author_type)
          AND read_by_user_at IS NULL
        """, nativeQuery = true)
    int markUnreadStaffMessagesRead(@Param("ticketId") UUID ticketId, @Param("readAt") LocalDateTime readAt);
}
