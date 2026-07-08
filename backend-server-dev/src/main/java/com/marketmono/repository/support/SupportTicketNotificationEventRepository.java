package com.marketmono.repository.support;

import com.marketmono.entity.support.SupportTicketNotificationEvent;
import com.marketmono.entity.support.SupportTicketNotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketNotificationEventRepository
        extends JpaRepository<SupportTicketNotificationEvent, UUID> {

    long countByStatus(SupportTicketNotificationStatus status);

    long countByStatusIn(Collection<SupportTicketNotificationStatus> statuses);

    @Query(value = """
        SELECT id
        FROM support_ticket_notification_events
        WHERE status = CAST(:status AS support_ticket_notification_status)
          AND next_attempt_at <= :now
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> findDueEventIdsForClaim(
        @Param("status") String status,
        @Param("now") LocalDateTime now,
        @Param("limit") int limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SupportTicketNotificationEvent event
        set event.status = :claimedStatus,
            event.updatedAt = :claimedAt,
            event.lastError = null
        where event.id in :eventIds
          and event.status = :pendingStatus
        """)
    int claimEvents(
        @Param("eventIds") Collection<UUID> eventIds,
        @Param("pendingStatus") SupportTicketNotificationStatus pendingStatus,
        @Param("claimedStatus") SupportTicketNotificationStatus claimedStatus,
        @Param("claimedAt") LocalDateTime claimedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SupportTicketNotificationEvent event
        set event.status = :pendingStatus,
            event.nextAttemptAt = :recoveredAt,
            event.updatedAt = :recoveredAt
        where event.status = :claimedStatus
          and event.updatedAt <= :leaseExpiredBefore
        """)
    int resetStaleClaims(
        @Param("claimedStatus") SupportTicketNotificationStatus claimedStatus,
        @Param("pendingStatus") SupportTicketNotificationStatus pendingStatus,
        @Param("leaseExpiredBefore") LocalDateTime leaseExpiredBefore,
        @Param("recoveredAt") LocalDateTime recoveredAt
    );

    @Query("""
        select event
        from SupportTicketNotificationEvent event
        join fetch event.ticket
        left join fetch event.message
        where event.id in :eventIds
          and event.status = :status
        order by event.createdAt asc
        """)
    List<SupportTicketNotificationEvent> findClaimedEventsByIdIn(
        @Param("eventIds") Collection<UUID> eventIds,
        @Param("status") SupportTicketNotificationStatus status
    );

    @Query(value = """
        SELECT COALESCE(
            MAX(EXTRACT(EPOCH FROM now() - created_at))
                FILTER (WHERE status IN (
                    CAST('PENDING' AS support_ticket_notification_status),
                    CAST('IN_PROGRESS' AS support_ticket_notification_status)
                )),
            0
        )::float8
        FROM support_ticket_notification_events
        """, nativeQuery = true)
    double oldestPendingAgeSeconds();
}
