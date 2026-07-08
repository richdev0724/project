package com.marketmono.service.support;

import com.marketmono.entity.support.SupportTicket;
import com.marketmono.entity.support.SupportTicketMessage;
import com.marketmono.entity.support.SupportTicketNotificationEvent;
import com.marketmono.entity.support.SupportTicketNotificationEventType;
import com.marketmono.entity.support.SupportTicketNotificationStatus;
import com.marketmono.repository.support.SupportTicketNotificationEventRepository;
import com.marketmono.service.auth.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupportTicketNotificationDispatcherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldClaimStaffReplyEventBeforeSendingAndMarkEventSent() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.staffReply(
            ticket,
            UUID.randomUUID(),
            "Agent Alice",
            "We fixed the account issue.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.STAFF_REPLY,
            "customer@example.com"
        );
        claimableEvent(eventRepository, event);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.IN_PROGRESS);
            return null;
        }).when(emailService).sendSupportTicketStaffReplyEmail("customer@example.com", ticket, message);

        dispatcher.dispatchPending(50);

        verify(emailService).sendSupportTicketStaffReplyEmail("customer@example.com", ticket, message);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.SENT);
        assertThat(event.getSentAt()).isEqualTo(LocalDateTime.now(CLOCK));
        verify(eventRepository).save(event);

        InOrder inOrder = inOrder(eventRepository, emailService);
        inOrder.verify(eventRepository).resetStaleClaims(
            SupportTicketNotificationStatus.IN_PROGRESS,
            SupportTicketNotificationStatus.PENDING,
            LocalDateTime.now(CLOCK).minusMinutes(15),
            LocalDateTime.now(CLOCK)
        );
        inOrder.verify(eventRepository).findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            LocalDateTime.now(CLOCK),
            50
        );
        inOrder.verify(eventRepository).claimEvents(
            List.of(event.getId()),
            SupportTicketNotificationStatus.PENDING,
            SupportTicketNotificationStatus.IN_PROGRESS,
            LocalDateTime.now(CLOCK)
        );
        inOrder.verify(eventRepository).findClaimedEventsByIdIn(
            List.of(event.getId()),
            SupportTicketNotificationStatus.IN_PROGRESS
        );
        inOrder.verify(emailService).sendSupportTicketStaffReplyEmail("customer@example.com", ticket, message);
        inOrder.verify(eventRepository).save(event);
    }

    @Test
    void shouldSendTicketCreatedEmailAndMarkEventSent() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "Initial support request.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.TICKET_CREATED,
            "support@marketmono.com"
        );
        claimableEvent(eventRepository, event);

        dispatcher.dispatchPending(50);

        verify(emailService).sendSupportTicketCreatedEmail("support@marketmono.com", ticket, message);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.SENT);
        verify(eventRepository).save(event);
    }

    @Test
    void shouldSendUserReplyEmailAndMarkEventSent() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "I still need help.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        claimableEvent(eventRepository, event);

        dispatcher.dispatchPending(50);

        verify(emailService).sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.SENT);
        verify(eventRepository).save(event);
    }

    @Test
    void shouldRecordFailureWithoutExposingMessageBodyInError() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 1);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "Sensitive body must not appear in failure detail.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        claimableEvent(eventRepository, event);
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP rejected Sensitive body must not appear"))
            .when(emailService)
            .sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);

        dispatcher.dispatchPending(50);

        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("IllegalStateException");
        verify(eventRepository).save(event);
    }

    @Test
    void shouldRetryBeforeFinalFailure() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "Retry this notification.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        claimableEvent(eventRepository, event);
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable"))
            .when(emailService)
            .sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);

        dispatcher.dispatchPending(50);

        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(1));
        assertThat(event.getLastError()).isEqualTo("IllegalStateException");
        verify(eventRepository).save(event);
    }

    @Test
    void shouldNotSendEmailWhenPendingEventIsNotClaimed() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "Another dispatcher claimed this first.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        when(eventRepository.findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            LocalDateTime.now(CLOCK),
            50
        )).thenReturn(List.of(event.getId()));
        when(eventRepository.claimEvents(
            List.of(event.getId()),
            SupportTicketNotificationStatus.PENDING,
            SupportTicketNotificationStatus.IN_PROGRESS,
            LocalDateTime.now(CLOCK)
        )).thenReturn(0);

        dispatcher.dispatchPending(50);

        verifyNoInteractions(emailService);
        verify(eventRepository, never()).findClaimedEventsByIdIn(
            anyList(),
            eq(SupportTicketNotificationStatus.IN_PROGRESS)
        );
        verify(eventRepository, never()).save(event);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.PENDING);
    }

    @Test
    void shouldRecoverStaleInProgressEventAndRetryIt() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "This claimed event was stranded by a crashed dispatcher.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        LocalDateTime now = LocalDateTime.now(CLOCK);
        event.markClaimed(now.minusMinutes(20));
        when(eventRepository.resetStaleClaims(
            SupportTicketNotificationStatus.IN_PROGRESS,
            SupportTicketNotificationStatus.PENDING,
            now.minusMinutes(15),
            now
        )).thenAnswer(invocation -> {
            event.setStatus(SupportTicketNotificationStatus.PENDING);
            event.setNextAttemptAt(now);
            event.setUpdatedAt(now);
            return 1;
        });
        when(eventRepository.findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            now,
            50
        )).thenAnswer(invocation ->
            event.getStatus() == SupportTicketNotificationStatus.PENDING
                ? List.of(event.getId())
                : List.of()
        );
        when(eventRepository.claimEvents(
            List.of(event.getId()),
            SupportTicketNotificationStatus.PENDING,
            SupportTicketNotificationStatus.IN_PROGRESS,
            now
        )).thenAnswer(invocation -> {
            event.markClaimed(now);
            return 1;
        });
        when(eventRepository.findClaimedEventsByIdIn(
            List.of(event.getId()),
            SupportTicketNotificationStatus.IN_PROGRESS
        )).thenReturn(List.of(event));

        dispatcher.dispatchPending(50);

        verify(emailService).sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.SENT);
        InOrder inOrder = inOrder(eventRepository, emailService);
        inOrder.verify(eventRepository).resetStaleClaims(
            SupportTicketNotificationStatus.IN_PROGRESS,
            SupportTicketNotificationStatus.PENDING,
            now.minusMinutes(15),
            now
        );
        inOrder.verify(eventRepository).findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            now,
            50
        );
        inOrder.verify(emailService).sendSupportTicketUserReplyEmail("support@marketmono.com", ticket, message);
    }

    @Test
    void shouldNotStealFreshInProgressEventBeforeLeaseExpires() {
        SupportTicketNotificationEventRepository eventRepository = mock(SupportTicketNotificationEventRepository.class);
        EmailService emailService = mock(EmailService.class);
        SupportTicketNotificationDispatcher dispatcher =
            new SupportTicketNotificationDispatcher(eventRepository, emailService, CLOCK, 3);
        SupportTicket ticket = ticket();
        SupportTicketMessage message = SupportTicketMessage.userReply(
            ticket,
            ticket.getUserUuid(),
            "This event is still being processed by another dispatcher.",
            LocalDateTime.now(CLOCK)
        );
        SupportTicketNotificationEvent event = pendingEvent(
            ticket,
            message,
            SupportTicketNotificationEventType.USER_REPLY,
            "support@marketmono.com"
        );
        LocalDateTime now = LocalDateTime.now(CLOCK);
        event.markClaimed(now.minusMinutes(5));
        when(eventRepository.resetStaleClaims(
            SupportTicketNotificationStatus.IN_PROGRESS,
            SupportTicketNotificationStatus.PENDING,
            now.minusMinutes(15),
            now
        )).thenReturn(0);
        when(eventRepository.findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            now,
            50
        )).thenAnswer(invocation ->
            event.getStatus() == SupportTicketNotificationStatus.PENDING
                ? List.of(event.getId())
                : List.of()
        );

        dispatcher.dispatchPending(50);

        verifyNoInteractions(emailService);
        verify(eventRepository, never()).claimEvents(
            anyList(),
            eq(SupportTicketNotificationStatus.PENDING),
            eq(SupportTicketNotificationStatus.IN_PROGRESS),
            eq(now)
        );
        verify(eventRepository, never()).save(event);
        assertThat(event.getStatus()).isEqualTo(SupportTicketNotificationStatus.IN_PROGRESS);
    }

    private static SupportTicket ticket() {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setTicketNumber("MM-0125");
        ticket.setUserUuid(UUID.randomUUID());
        ticket.setUserEmail("customer@example.com");
        ticket.setSubject("Account issue");
        return ticket;
    }

    private static SupportTicketNotificationEvent pendingEvent(
            SupportTicket ticket,
            SupportTicketMessage message,
            SupportTicketNotificationEventType eventType,
            String recipientEmail) {
        SupportTicketNotificationEvent event = SupportTicketNotificationEvent.pending(
            ticket,
            message,
            eventType,
            recipientEmail,
            LocalDateTime.now(CLOCK)
        );
        event.setId(UUID.randomUUID());
        return event;
    }

    private static void claimableEvent(
            SupportTicketNotificationEventRepository eventRepository,
            SupportTicketNotificationEvent event) {
        List<UUID> eventIds = List.of(event.getId());
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(eventRepository.findDueEventIdsForClaim(
            SupportTicketNotificationStatus.PENDING.name(),
            now,
            50
        )).thenReturn(eventIds);
        when(eventRepository.claimEvents(
            eventIds,
            SupportTicketNotificationStatus.PENDING,
            SupportTicketNotificationStatus.IN_PROGRESS,
            now
        )).thenAnswer(invocation -> {
            event.markClaimed(now);
            return eventIds.size();
        });
        when(eventRepository.findClaimedEventsByIdIn(
            eventIds,
            SupportTicketNotificationStatus.IN_PROGRESS
        )).thenReturn(List.of(event));
    }
}
