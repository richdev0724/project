package com.marketmono.service.support;

import com.marketmono.entity.support.SupportTicketNotificationEvent;
import com.marketmono.entity.support.SupportTicketNotificationEventType;
import com.marketmono.entity.support.SupportTicketNotificationStatus;
import com.marketmono.repository.support.SupportTicketNotificationEventRepository;
import com.marketmono.service.auth.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SupportTicketNotificationDispatcher {

    private static final long DEFAULT_CLAIM_LEASE_MINUTES = 15;

    private final SupportTicketNotificationEventRepository eventRepository;
    private final EmailService emailService;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration claimLease;
    private final TransactionOperations transactionOperations;

    @Autowired
    public SupportTicketNotificationDispatcher(
            SupportTicketNotificationEventRepository eventRepository,
            EmailService emailService,
            PlatformTransactionManager transactionManager,
            @Value("${support.tickets.notifications.max-attempts:3}") int maxAttempts,
            @Value("${support.tickets.notifications.claim-lease-minutes:15}") long claimLeaseMinutes) {
        this(
            eventRepository,
            emailService,
            Clock.systemUTC(),
            maxAttempts,
            claimLease(claimLeaseMinutes),
            requiresNewTransactions(transactionManager)
        );
    }

    SupportTicketNotificationDispatcher(
            SupportTicketNotificationEventRepository eventRepository,
            EmailService emailService,
            Clock clock,
            int maxAttempts) {
        this(
            eventRepository,
            emailService,
            clock,
            maxAttempts,
            claimLease(DEFAULT_CLAIM_LEASE_MINUTES),
            immediateTransactions()
        );
    }

    SupportTicketNotificationDispatcher(
            SupportTicketNotificationEventRepository eventRepository,
            EmailService emailService,
            Clock clock,
            int maxAttempts,
            TransactionOperations transactionOperations) {
        this(
            eventRepository,
            emailService,
            clock,
            maxAttempts,
            claimLease(DEFAULT_CLAIM_LEASE_MINUTES),
            transactionOperations
        );
    }

    SupportTicketNotificationDispatcher(
            SupportTicketNotificationEventRepository eventRepository,
            EmailService emailService,
            Clock clock,
            int maxAttempts,
            Duration claimLease,
            TransactionOperations transactionOperations) {
        this.eventRepository = eventRepository;
        this.emailService = emailService;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.claimLease = claimLease;
        this.transactionOperations = transactionOperations;
    }

    @Scheduled(fixedDelayString = "${support.tickets.notifications.dispatch-delay-ms:60000}")
    public void dispatchScheduled() {
        dispatchPending(50);
    }

    public void dispatchPending(int limit) {
        if (limit <= 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<SupportTicketNotificationEvent> events = claimDueEvents(limit, now);

        events.forEach(this::dispatchOne);
    }

    private List<SupportTicketNotificationEvent> claimDueEvents(int limit, LocalDateTime now) {
        return transactionOperations.execute(status -> {
            recoverStaleClaims(now);

            List<UUID> eventIds = eventRepository.findDueEventIdsForClaim(
                SupportTicketNotificationStatus.PENDING.name(),
                now,
                limit
            );
            if (eventIds.isEmpty()) {
                return List.of();
            }

            int claimed = eventRepository.claimEvents(
                eventIds,
                SupportTicketNotificationStatus.PENDING,
                SupportTicketNotificationStatus.IN_PROGRESS,
                now
            );
            if (claimed == 0) {
                return List.of();
            }

            return eventRepository.findClaimedEventsByIdIn(eventIds, SupportTicketNotificationStatus.IN_PROGRESS);
        });
    }

    private void recoverStaleClaims(LocalDateTime now) {
        LocalDateTime leaseExpiredBefore = now.minus(claimLease);
        int recovered = eventRepository.resetStaleClaims(
            SupportTicketNotificationStatus.IN_PROGRESS,
            SupportTicketNotificationStatus.PENDING,
            leaseExpiredBefore,
            now
        );
        if (recovered > 0) {
            log.info("Recovered {} stale support ticket notification claim(s)", recovered);
        }
    }

    private void dispatchOne(SupportTicketNotificationEvent event) {
        try {
            send(event);
            markSent(event);
        } catch (Exception exception) {
            markFailed(event, exception);
            log.warn(
                "Support ticket notification dispatch failed for event {} type {}: {}",
                event.getId(),
                event.getEventType(),
                exception.getClass().getSimpleName()
            );
        }
    }

    private void send(SupportTicketNotificationEvent event) {
        SupportTicketNotificationEventType eventType = event.getEventType();
        if (eventType == SupportTicketNotificationEventType.STAFF_REPLY) {
            emailService.sendSupportTicketStaffReplyEmail(
                event.getRecipientEmail(),
                event.getTicket(),
                event.getMessage()
            );
            return;
        }
        if (eventType == SupportTicketNotificationEventType.USER_REPLY) {
            emailService.sendSupportTicketUserReplyEmail(
                event.getRecipientEmail(),
                event.getTicket(),
                event.getMessage()
            );
            return;
        }
        emailService.sendSupportTicketCreatedEmail(
            event.getRecipientEmail(),
            event.getTicket(),
            event.getMessage()
        );
    }

    private void markSent(SupportTicketNotificationEvent event) {
        transactionOperations.executeWithoutResult(status -> {
            event.markSent(LocalDateTime.now(clock));
            eventRepository.save(event);
        });
    }

    private void markFailed(SupportTicketNotificationEvent event, Exception exception) {
        transactionOperations.executeWithoutResult(status -> {
            event.markFailed(exception, LocalDateTime.now(clock), maxAttempts);
            eventRepository.save(event);
        });
    }

    private static TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    private static TransactionOperations requiresNewTransactions(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private static Duration claimLease(long claimLeaseMinutes) {
        return Duration.ofMinutes(Math.max(1, claimLeaseMinutes));
    }
}
