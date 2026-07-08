package com.marketmono.service.support;

import com.marketmono.dto.support.CreateSupportTicketRequest;
import com.marketmono.dto.support.SupportTicketDto;
import com.marketmono.dto.support.SupportTicketReplyRequest;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.support.SupportTicket;
import com.marketmono.entity.support.SupportTicketMessage;
import com.marketmono.entity.support.SupportTicketMessageAuthorType;
import com.marketmono.entity.support.SupportTicketNotificationEvent;
import com.marketmono.entity.support.SupportTicketNotificationEventType;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import com.marketmono.repository.support.SupportTicketMessageRepository;
import com.marketmono.repository.support.SupportTicketNotificationEventRepository;
import com.marketmono.repository.support.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private SupportTicketMessageRepository messageRepository;

    @Mock
    private SupportTicketNotificationEventRepository notificationEventRepository;

    @Mock
    private SupportTicketNumberGenerator ticketNumberGenerator;

    @Mock
    private SupportTicketRateLimitService rateLimitService;

    private SupportTicketService service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(
            ticketRepository,
            messageRepository,
            notificationEventRepository,
            ticketNumberGenerator,
            rateLimitService,
            CLOCK
        );
    }

    @Test
    void shouldCreateOpenTicketWithInitialMessageAndSupportNotification() {
        User user = user("customer@example.com");
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationEventRepository.save(any(SupportTicketNotificationEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketNumberGenerator.nextTicketNumber()).thenReturn("MM-0123");

        SupportTicketDto result = service.createTicket(
            user,
            new CreateSupportTicketRequest("  Login issue  ", "  I cannot sign in from settings.  ")
        );

        ArgumentCaptor<SupportTicket> ticketCaptor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        SupportTicket savedTicket = ticketCaptor.getValue();
        assertThat(savedTicket.getTicketNumber()).isEqualTo("MM-0123");
        assertThat(savedTicket.getUser()).isSameAs(user);
        assertThat(savedTicket.getUserUuid()).isEqualTo(user.getId());
        assertThat(savedTicket.getUserEmail()).isEqualTo("customer@example.com");
        assertThat(savedTicket.getSubject()).isEqualTo("Login issue");
        assertThat(savedTicket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(savedTicket.getPriority()).isEqualTo(SupportTicketPriority.MEDIUM);

        ArgumentCaptor<SupportTicketMessage> messageCaptor = ArgumentCaptor.forClass(SupportTicketMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        SupportTicketMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getTicket()).isSameAs(savedTicket);
        assertThat(savedMessage.getAuthorType()).isEqualTo(SupportTicketMessageAuthorType.USER);
        assertThat(savedMessage.getAuthorUserUuid()).isEqualTo(user.getId());
        assertThat(savedMessage.getBody()).isEqualTo("I cannot sign in from settings.");

        ArgumentCaptor<SupportTicketNotificationEvent> eventCaptor =
            ArgumentCaptor.forClass(SupportTicketNotificationEvent.class);
        verify(notificationEventRepository).save(eventCaptor.capture());
        SupportTicketNotificationEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(SupportTicketNotificationEventType.TICKET_CREATED);
        assertThat(event.getTicket()).isSameAs(savedTicket);
        assertThat(event.getMessage()).isSameAs(savedMessage);
        assertThat(event.getRecipientEmail()).isEqualTo("support@marketmono.com");

        verify(rateLimitService).checkCreateAllowed(user.getId());
        assertThat(result.ticketNumber()).isEqualTo("MM-0123");
        assertThat(result.status()).isEqualTo(SupportTicketStatus.OPEN);
    }

    @Test
    void shouldRejectReplyToClosedTicketWithoutAppendingMessage() {
        User user = user("customer@example.com");
        SupportTicket ticket = ticket(user, SupportTicketStatus.CLOSED);
        when(ticketRepository.findByIdAndUserUuid(ticket.getId(), user.getId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reply(user, ticket.getId(), new SupportTicketReplyRequest("Please reopen this.")))
            .isInstanceOf(SupportTicketService.SupportTicketClosedException.class);

        verify(messageRepository, never()).save(any());
        verify(notificationEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectCrossUserTicketLookup() {
        User user = user("customer@example.com");
        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.findByIdAndUserUuid(ticketId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicket(user, ticketId))
            .isInstanceOf(SupportTicketService.SupportTicketNotFoundException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void shouldCreateUserReplyNotificationEvent() {
        User user = user("customer@example.com");
        SupportTicket ticket = ticket(user, SupportTicketStatus.PENDING_RESPONSE);
        when(ticketRepository.findByIdAndUserUuid(ticket.getId(), user.getId())).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(SupportTicketMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())).thenReturn(List.of());
        when(notificationEventRepository.save(any(SupportTicketNotificationEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.reply(user, ticket.getId(), new SupportTicketReplyRequest("I still need help."));

        ArgumentCaptor<SupportTicketNotificationEvent> eventCaptor =
            ArgumentCaptor.forClass(SupportTicketNotificationEvent.class);
        verify(notificationEventRepository).save(eventCaptor.capture());
        SupportTicketNotificationEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo(SupportTicketNotificationEventType.USER_REPLY);
        assertThat(event.getRecipientEmail()).isEqualTo("support@marketmono.com");
        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);
    }

    @Test
    void shouldClearUnreadStaffMessagesForOwnedTicket() {
        User user = user("customer@example.com");
        SupportTicket ticket = ticket(user, SupportTicketStatus.PENDING_RESPONSE);
        ticket.setUnreadStaffMessageCount(2);
        when(ticketRepository.findByIdAndUserUuid(ticket.getId(), user.getId())).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.findByUserUuidAndUnreadStaffMessageCountGreaterThanOrderByUpdatedAtDesc(user.getId(), 0))
            .thenReturn(List.of());

        var result = service.markRead(user, ticket.getId());

        verify(messageRepository).markUnreadStaffMessagesRead(ticket.getId(), LocalDateTime.now(CLOCK));
        assertThat(ticket.getUnreadStaffMessageCount()).isZero();
        assertThat(result.unreadCount()).isZero();
        assertThat(result.ticketIds()).isEmpty();
    }

    @Test
    void shouldRenderStaffMessagesAsMarketMonoSupportInUserDto() {
        User user = user("customer@example.com");
        SupportTicket ticket = ticket(user, SupportTicketStatus.PENDING_RESPONSE);
        SupportTicketMessage staffMessage = SupportTicketMessage.staffReply(
            ticket,
            UUID.randomUUID(),
            "Agent Alice",
            "We have reset the stuck session.",
            LocalDateTime.now(CLOCK)
        );
        when(ticketRepository.findByIdAndUserUuid(ticket.getId(), user.getId())).thenReturn(Optional.of(ticket));
        when(messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())).thenReturn(List.of(staffMessage));

        SupportTicketDto result = service.getTicket(user, ticket.getId());

        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().authorDisplayName()).isEqualTo("Market Mono Support");
        assertThat(result.messages().getFirst().body()).isEqualTo("We have reset the stuck session.");
    }

    private static User user(String email) {
        User user = User.builder()
            .id(UUID.randomUUID())
            .displayName("Customer User")
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail(email);
        return user;
    }

    private static SupportTicket ticket(User user, SupportTicketStatus status) {
        SupportTicket ticket = new SupportTicket();
        ticket.setId(UUID.randomUUID());
        ticket.setTicketNumber("MM-0124");
        ticket.setUser(user);
        ticket.setUserUuid(user.getId());
        ticket.setUserEmail(user.getEmail());
        ticket.setSubject("Existing issue");
        ticket.setStatus(status);
        ticket.setPriority(SupportTicketPriority.MEDIUM);
        ticket.setCreatedAt(LocalDateTime.now(CLOCK));
        ticket.setUpdatedAt(LocalDateTime.now(CLOCK));
        return ticket;
    }
}
