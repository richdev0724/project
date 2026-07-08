package com.marketmono.repository.support;

import com.marketmono.entity.support.SupportTicket;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByUserUuidOrderByUpdatedAtDesc(UUID userUuid);

    Optional<SupportTicket> findByIdAndUserUuid(UUID id, UUID userUuid);

    List<SupportTicket> findByUserUuidAndUnreadStaffMessageCountGreaterThanOrderByUpdatedAtDesc(
        UUID userUuid,
        int unreadStaffMessageCount
    );

    long countByStatus(SupportTicketStatus status);

    long countByStatusIn(Collection<SupportTicketStatus> statuses);

    long countByStatusAndPriorityAndAssignedAdminIdIsNull(
        SupportTicketStatus status,
        SupportTicketPriority priority
    );

    long countByStatusAndPriorityAndAssignedAdminIdIsNotNull(
        SupportTicketStatus status,
        SupportTicketPriority priority
    );

    long countByCreatedAtGreaterThanEqual(LocalDateTime createdAt);

    long countByStatusAndClosedAtGreaterThanEqual(SupportTicketStatus status, LocalDateTime closedAt);

    @Query(value = """
        SELECT COALESCE(
            MAX(EXTRACT(EPOCH FROM now() - COALESCE(last_user_reply_at, created_at)))
                FILTER (WHERE status = CAST('OPEN' AS support_ticket_status)),
            0
        )::float8
        FROM support_tickets
        """, nativeQuery = true)
    double oldestOpenAgeSeconds();
}
