package com.marketmono.service.support;

import com.marketmono.entity.support.SupportTicketMessageAuthorType;
import com.marketmono.entity.support.SupportTicketNotificationStatus;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import com.marketmono.repository.support.SupportTicketMessageRepository;
import com.marketmono.repository.support.SupportTicketNotificationEventRepository;
import com.marketmono.repository.support.SupportTicketRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportTicketMetricsTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-06-03T10:00:00Z"),
        ZoneOffset.UTC
    );

    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final SupportTicketMessageRepository messageRepository = mock(SupportTicketMessageRepository.class);
    private final SupportTicketNotificationEventRepository notificationEventRepository =
        mock(SupportTicketNotificationEventRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void springContextCreatesMetricsComponentWithProductionConstructor() {
        assertThatCode(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(SupportTicketRepository.class, () -> ticketRepository);
                context.registerBean(SupportTicketMessageRepository.class, () -> messageRepository);
                context.registerBean(SupportTicketNotificationEventRepository.class, () -> notificationEventRepository);
                context.registerBean(SimpleMeterRegistry.class, () -> registry);
                context.register(SupportTicketMetrics.class);

                context.refresh();

                assertThat(context.getBean(SupportTicketMetrics.class)).isNotNull();
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void registersSupportTicketQueueActivityAndNotificationGauges() {
        LocalDateTime activityWindowStart = LocalDateTime.of(2026, 6, 2, 10, 0);

        when(ticketRepository.countByStatusIn(anyCollection())).thenReturn(7L);
        when(ticketRepository.countByStatus(SupportTicketStatus.OPEN)).thenReturn(4L);
        when(ticketRepository.countByStatusAndPriorityAndAssignedAdminIdIsNull(
            SupportTicketStatus.OPEN,
            SupportTicketPriority.HIGH
        )).thenReturn(2L);
        when(ticketRepository.oldestOpenAgeSeconds()).thenReturn(3600.0);
        when(ticketRepository.countByCreatedAtGreaterThanEqual(activityWindowStart)).thenReturn(5L);
        when(ticketRepository.countByStatusAndClosedAtGreaterThanEqual(
            SupportTicketStatus.CLOSED,
            activityWindowStart
        )).thenReturn(1L);
        when(messageRepository.countByAuthorTypeAndCreatedAtGreaterThanEqual(
            SupportTicketMessageAuthorType.USER,
            activityWindowStart
        )).thenReturn(9L);
        when(messageRepository.countByAuthorTypeAndCreatedAtGreaterThanEqual(
            SupportTicketMessageAuthorType.STAFF,
            activityWindowStart
        )).thenReturn(6L);
        when(notificationEventRepository.countByStatusIn(anyCollection())).thenReturn(3L);
        when(notificationEventRepository.countByStatus(SupportTicketNotificationStatus.FAILED)).thenReturn(1L);
        when(notificationEventRepository.oldestPendingAgeSeconds()).thenReturn(600.0);

        new SupportTicketMetrics(
            ticketRepository,
            messageRepository,
            notificationEventRepository,
            registry,
            FIXED_CLOCK
        ).registerMetrics();

        assertThat(gauge("marketmono.support.tickets.count", "status", "OPEN_OR_PENDING", "priority", "ALL", "assignment", "ALL"))
            .isEqualTo(7.0);
        assertThat(gauge("marketmono.support.tickets.count", "status", "OPEN", "priority", "ALL", "assignment", "ALL"))
            .isEqualTo(4.0);
        assertThat(gauge("marketmono.support.tickets.count", "status", "OPEN", "priority", "HIGH", "assignment", "unassigned"))
            .isEqualTo(2.0);
        assertThat(gauge("marketmono.support.tickets.oldest.open.age.seconds", "status", "OPEN", "priority", "ALL", "assignment", "ALL"))
            .isEqualTo(3600.0);
        assertThat(gauge("marketmono.support.ticket.activity.count", "activity", "created_24h"))
            .isEqualTo(5.0);
        assertThat(gauge("marketmono.support.ticket.activity.count", "activity", "closed_24h"))
            .isEqualTo(1.0);
        assertThat(gauge("marketmono.support.ticket.activity.count", "activity", "user_messages_24h"))
            .isEqualTo(9.0);
        assertThat(gauge("marketmono.support.ticket.activity.count", "activity", "staff_messages_24h"))
            .isEqualTo(6.0);
        assertThat(gauge("marketmono.support.ticket.notifications.count", "status", "PENDING_OR_IN_PROGRESS", "event_type", "ALL", "recipient_type", "ALL"))
            .isEqualTo(3.0);
        assertThat(gauge("marketmono.support.ticket.notifications.count", "status", "FAILED", "event_type", "ALL", "recipient_type", "ALL"))
            .isEqualTo(1.0);
        assertThat(gauge("marketmono.support.ticket.notifications.oldest.pending.age.seconds", "status", "PENDING_OR_IN_PROGRESS", "event_type", "ALL", "recipient_type", "ALL"))
            .isEqualTo(600.0);
    }

    private double gauge(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }
}
