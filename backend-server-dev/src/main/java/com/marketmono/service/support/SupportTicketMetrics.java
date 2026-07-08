package com.marketmono.service.support;

import com.marketmono.entity.support.SupportTicketMessageAuthorType;
import com.marketmono.entity.support.SupportTicketNotificationStatus;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import com.marketmono.repository.support.SupportTicketMessageRepository;
import com.marketmono.repository.support.SupportTicketNotificationEventRepository;
import com.marketmono.repository.support.SupportTicketRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

@Component
@Slf4j
public class SupportTicketMetrics {

    private static final String ALL = "ALL";
    private static final List<SupportTicketStatus> ACTIVE_TICKET_STATUSES = List.of(
        SupportTicketStatus.OPEN,
        SupportTicketStatus.PENDING_RESPONSE
    );
    private static final List<SupportTicketNotificationStatus> NOTIFICATION_BACKLOG_STATUSES = List.of(
        SupportTicketNotificationStatus.PENDING,
        SupportTicketNotificationStatus.IN_PROGRESS
    );

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketNotificationEventRepository notificationEventRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public SupportTicketMetrics(
            SupportTicketRepository ticketRepository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketNotificationEventRepository notificationEventRepository,
            MeterRegistry meterRegistry) {
        this(ticketRepository, messageRepository, notificationEventRepository, meterRegistry, Clock.systemUTC());
    }

    SupportTicketMetrics(
            SupportTicketRepository ticketRepository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketNotificationEventRepository notificationEventRepository,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.notificationEventRepository = notificationEventRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @PostConstruct
    void registerMetrics() {
        registerTicketQueueGauges();
        registerTicketActivityGauges();
        registerNotificationGauges();
    }

    private void registerTicketQueueGauges() {
        registerTicketCountGauge(
            "OPEN_OR_PENDING",
            ALL,
            ALL,
            () -> ticketRepository.countByStatusIn(ACTIVE_TICKET_STATUSES)
        );

        for (SupportTicketStatus status : SupportTicketStatus.values()) {
            registerTicketCountGauge(status.name(), ALL, ALL, () -> ticketRepository.countByStatus(status));
        }

        for (SupportTicketStatus status : ACTIVE_TICKET_STATUSES) {
            for (SupportTicketPriority priority : SupportTicketPriority.values()) {
                registerTicketCountGauge(
                    status.name(),
                    priority.name(),
                    "unassigned",
                    () -> ticketRepository.countByStatusAndPriorityAndAssignedAdminIdIsNull(status, priority)
                );
                registerTicketCountGauge(
                    status.name(),
                    priority.name(),
                    "assigned",
                    () -> ticketRepository.countByStatusAndPriorityAndAssignedAdminIdIsNotNull(status, priority)
                );
            }
        }

        Gauge.builder(
                "marketmono.support.tickets.oldest.open.age.seconds",
                ticketRepository,
                repository -> metricValue(repository::oldestOpenAgeSeconds)
            )
            .description("Age in seconds since latest user message or creation for the oldest open support ticket")
            .tag("status", SupportTicketStatus.OPEN.name())
            .tag("priority", ALL)
            .tag("assignment", ALL)
            .register(meterRegistry);
    }

    private void registerTicketActivityGauges() {
        registerActivityGauge(
            "created_24h",
            () -> ticketRepository.countByCreatedAtGreaterThanEqual(activityWindowStart())
        );
        registerActivityGauge(
            "closed_24h",
            () -> ticketRepository.countByStatusAndClosedAtGreaterThanEqual(
                SupportTicketStatus.CLOSED,
                activityWindowStart()
            )
        );
        registerActivityGauge(
            "user_messages_24h",
            () -> messageRepository.countByAuthorTypeAndCreatedAtGreaterThanEqual(
                SupportTicketMessageAuthorType.USER,
                activityWindowStart()
            )
        );
        registerActivityGauge(
            "staff_messages_24h",
            () -> messageRepository.countByAuthorTypeAndCreatedAtGreaterThanEqual(
                SupportTicketMessageAuthorType.STAFF,
                activityWindowStart()
            )
        );
    }

    private void registerNotificationGauges() {
        registerNotificationCountGauge(
            "PENDING_OR_IN_PROGRESS",
            ALL,
            ALL,
            () -> notificationEventRepository.countByStatusIn(NOTIFICATION_BACKLOG_STATUSES)
        );

        for (SupportTicketNotificationStatus status : SupportTicketNotificationStatus.values()) {
            registerNotificationCountGauge(
                status.name(),
                ALL,
                ALL,
                () -> notificationEventRepository.countByStatus(status)
            );
        }

        Gauge.builder(
                "marketmono.support.ticket.notifications.oldest.pending.age.seconds",
                notificationEventRepository,
                repository -> metricValue(repository::oldestPendingAgeSeconds)
            )
            .description("Age in seconds of the oldest pending or in-progress support ticket notification")
            .tag("status", "PENDING_OR_IN_PROGRESS")
            .tag("event_type", ALL)
            .tag("recipient_type", ALL)
            .register(meterRegistry);
    }

    private void registerTicketCountGauge(
            String status,
            String priority,
            String assignment,
            Supplier<? extends Number> supplier) {
        Gauge.builder("marketmono.support.tickets.count", ticketRepository, repository -> metricValue(supplier))
            .description("Support ticket count by status, priority, and assignment")
            .tag("status", status)
            .tag("priority", priority)
            .tag("assignment", assignment)
            .register(meterRegistry);
    }

    private void registerActivityGauge(String activity, Supplier<? extends Number> supplier) {
        Gauge.builder("marketmono.support.ticket.activity.count", this, metrics -> metricValue(supplier))
            .description("Support ticket activity count over the last 24 hours")
            .tag("activity", activity)
            .register(meterRegistry);
    }

    private void registerNotificationCountGauge(
            String status,
            String eventType,
            String recipientType,
            Supplier<? extends Number> supplier) {
        Gauge.builder(
                "marketmono.support.ticket.notifications.count",
                notificationEventRepository,
                repository -> metricValue(supplier)
            )
            .description("Support ticket notification event count by status")
            .tag("status", status)
            .tag("event_type", eventType)
            .tag("recipient_type", recipientType)
            .register(meterRegistry);
    }

    private LocalDateTime activityWindowStart() {
        return LocalDateTime.now(clock).minusHours(24);
    }

    private static double metricValue(Supplier<? extends Number> supplier) {
        try {
            Number value = supplier.get();
            return value == null ? 0.0 : value.doubleValue();
        } catch (RuntimeException e) {
            log.debug("Unable to collect support ticket observability metric: {}", e.getMessage());
            return Double.NaN;
        }
    }
}
