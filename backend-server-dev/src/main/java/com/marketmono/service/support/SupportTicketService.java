package com.marketmono.service.support;

import com.marketmono.dto.support.CreateSupportTicketRequest;
import com.marketmono.dto.support.SupportTicketDto;
import com.marketmono.dto.support.SupportTicketMessageDto;
import com.marketmono.dto.support.SupportTicketReplyRequest;
import com.marketmono.dto.support.SupportTicketUnreadResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SupportTicketService {

    private static final int SUBJECT_MAX_LENGTH = 160;
    private static final int MESSAGE_MAX_LENGTH = 5000;
    private static final String STAFF_USER_DISPLAY_NAME = "Market Mono Support";

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketNotificationEventRepository notificationEventRepository;
    private final SupportTicketNumberGenerator ticketNumberGenerator;
    private final SupportTicketRateLimitService rateLimitService;
    private final Clock clock;
    private final String supportRecipientEmail;

    @Autowired
    public SupportTicketService(
            SupportTicketRepository ticketRepository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketNotificationEventRepository notificationEventRepository,
            SupportTicketNumberGenerator ticketNumberGenerator,
            SupportTicketRateLimitService rateLimitService,
            @Value("${support.tickets.notifications.support-email:support@marketmono.com}")
            String supportRecipientEmail) {
        this(
            ticketRepository,
            messageRepository,
            notificationEventRepository,
            ticketNumberGenerator,
            rateLimitService,
            Clock.systemUTC(),
            supportRecipientEmail
        );
    }

    SupportTicketService(
            SupportTicketRepository ticketRepository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketNotificationEventRepository notificationEventRepository,
            SupportTicketNumberGenerator ticketNumberGenerator,
            SupportTicketRateLimitService rateLimitService,
            Clock clock) {
        this(
            ticketRepository,
            messageRepository,
            notificationEventRepository,
            ticketNumberGenerator,
            rateLimitService,
            clock,
            "support@marketmono.com"
        );
    }

    private SupportTicketService(
            SupportTicketRepository ticketRepository,
            SupportTicketMessageRepository messageRepository,
            SupportTicketNotificationEventRepository notificationEventRepository,
            SupportTicketNumberGenerator ticketNumberGenerator,
            SupportTicketRateLimitService rateLimitService,
            Clock clock,
            String supportRecipientEmail) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.notificationEventRepository = notificationEventRepository;
        this.ticketNumberGenerator = ticketNumberGenerator;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
        this.supportRecipientEmail = supportRecipientEmail;
    }

    @Transactional
    public SupportTicketDto createTicket(User user, CreateSupportTicketRequest request) {
        String subject = requireBoundedText(request.subject(), SUBJECT_MAX_LENGTH, "Subject");
        String body = requireBoundedText(request.message(), MESSAGE_MAX_LENGTH, "Message");
        rateLimitService.checkCreateAllowed(user.getId());

        LocalDateTime now = LocalDateTime.now(clock);
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(ticketNumberGenerator.nextTicketNumber());
        ticket.setUser(user);
        ticket.setUserUuid(user.getId());
        ticket.setUserEmail(user.getEmail());
        ticket.setSubject(subject);
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setPriority(SupportTicketPriority.MEDIUM);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setLastUserReplyAt(now);
        SupportTicket savedTicket = ticketRepository.save(ticket);

        SupportTicketMessage message = SupportTicketMessage.userReply(savedTicket, user.getId(), body, now);
        SupportTicketMessage savedMessage = messageRepository.save(message);
        notificationEventRepository.save(SupportTicketNotificationEvent.pending(
            savedTicket,
            savedMessage,
            SupportTicketNotificationEventType.TICKET_CREATED,
            supportRecipientEmail,
            now
        ));

        return toDto(savedTicket, List.of(savedMessage));
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> listTickets(User user) {
        return ticketRepository.findByUserUuidOrderByUpdatedAtDesc(user.getId()).stream()
            .map(ticket -> toDto(ticket, List.of()))
            .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicketDto getTicket(User user, UUID ticketId) {
        SupportTicket ticket = requireOwnedTicket(user, ticketId);
        List<SupportTicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return toDto(ticket, messages);
    }

    @Transactional
    public SupportTicketDto reply(User user, UUID ticketId, SupportTicketReplyRequest request) {
        String body = requireBoundedText(request.message(), MESSAGE_MAX_LENGTH, "Message");
        rateLimitService.checkReplyAllowed(user.getId());

        SupportTicket ticket = requireOwnedTicket(user, ticketId);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new SupportTicketClosedException("Closed support tickets are read-only");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ticket.markUserReply(now);
        SupportTicket savedTicket = ticketRepository.save(ticket);
        SupportTicketMessage message = SupportTicketMessage.userReply(savedTicket, user.getId(), body, now);
        SupportTicketMessage savedMessage = messageRepository.save(message);
        notificationEventRepository.save(SupportTicketNotificationEvent.pending(
            savedTicket,
            savedMessage,
            SupportTicketNotificationEventType.USER_REPLY,
            supportRecipientEmail,
            now
        ));

        List<SupportTicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (messages.isEmpty()) {
            messages = List.of(savedMessage);
        }
        return toDto(savedTicket, messages);
    }

    @Transactional(readOnly = true)
    public SupportTicketUnreadResponse getUnreadSummary(User user) {
        List<SupportTicket> unreadTickets =
            ticketRepository.findByUserUuidAndUnreadStaffMessageCountGreaterThanOrderByUpdatedAtDesc(user.getId(), 0);
        int unreadCount = unreadTickets.stream()
            .mapToInt(SupportTicket::getUnreadStaffMessageCount)
            .sum();
        List<UUID> ticketIds = unreadTickets.stream()
            .map(SupportTicket::getId)
            .toList();
        return new SupportTicketUnreadResponse(unreadCount, ticketIds);
    }

    @Transactional
    public SupportTicketUnreadResponse markRead(User user, UUID ticketId) {
        SupportTicket ticket = requireOwnedTicket(user, ticketId);
        LocalDateTime now = LocalDateTime.now(clock);
        messageRepository.markUnreadStaffMessagesRead(ticket.getId(), now);
        ticket.markRead(now);
        ticketRepository.save(ticket);
        return getUnreadSummary(user);
    }

    private SupportTicket requireOwnedTicket(User user, UUID ticketId) {
        return ticketRepository.findByIdAndUserUuid(ticketId, user.getId())
            .orElseThrow(() -> new SupportTicketNotFoundException("Support ticket not found"));
    }

    private SupportTicketDto toDto(SupportTicket ticket, List<SupportTicketMessage> messages) {
        return new SupportTicketDto(
            ticket.getId(),
            ticket.getTicketNumber(),
            ticket.getStatus(),
            ticket.getPriority(),
            ticket.getSubject(),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt(),
            ticket.getClosedAt(),
            ticket.getUnreadStaffMessageCount(),
            ticket.getLastStaffReplyAt(),
            messages.stream().map(this::toMessageDto).toList()
        );
    }

    private SupportTicketMessageDto toMessageDto(SupportTicketMessage message) {
        String displayName = message.getAuthorType() == SupportTicketMessageAuthorType.STAFF
            ? STAFF_USER_DISPLAY_NAME
            : "You";
        return new SupportTicketMessageDto(
            message.getId(),
            message.getAuthorType(),
            displayName,
            message.getBody(),
            message.getCreatedAt(),
            message.getReadByUserAt()
        );
    }

    private String requireBoundedText(String rawValue, int maxLength, String fieldName) {
        if (!StringUtils.hasText(rawValue)) {
            throw new SupportTicketValidationException(fieldName + " is required");
        }
        String value = rawValue.trim();
        if (value.length() > maxLength) {
            throw new SupportTicketValidationException(fieldName + " must be " + maxLength + " characters or less");
        }
        return value;
    }

    public static class SupportTicketNotFoundException extends RuntimeException {
        public SupportTicketNotFoundException(String message) {
            super(message);
        }
    }

    public static class SupportTicketValidationException extends RuntimeException {
        public SupportTicketValidationException(String message) {
            super(message);
        }
    }

    public static class SupportTicketClosedException extends RuntimeException {
        public SupportTicketClosedException(String message) {
            super(message);
        }
    }
}
