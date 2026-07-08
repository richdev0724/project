package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.support.CreateSupportTicketRequest;
import com.marketmono.dto.support.SupportTicketDto;
import com.marketmono.dto.support.SupportTicketReplyRequest;
import com.marketmono.dto.support.SupportTicketUnreadResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.support.SupportTicketPriority;
import com.marketmono.entity.support.SupportTicketStatus;
import com.marketmono.service.support.SupportTicketRateLimitExceededException;
import com.marketmono.service.support.SupportTicketRateLimiterUnavailableException;
import com.marketmono.service.support.SupportTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportTicketControllerTest {

    private SupportTicketService supportTicketService;
    private SupportTicketController controller;

    @BeforeEach
    void setUp() {
        supportTicketService = mock(SupportTicketService.class);
        controller = new SupportTicketController(supportTicketService);
    }

    @Test
    void shouldCreateTicketForAuthenticatedUserOnly() {
        User user = user();
        CreateSupportTicketRequest request = new CreateSupportTicketRequest("Subject", "Message");
        SupportTicketDto dto = dto(user.getId());
        when(supportTicketService.createTicket(user, request)).thenReturn(dto);

        ResponseEntity<SupportTicketDto> response = controller.createTicket(new UserPrincipal(user), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(dto);
        verify(supportTicketService).createTicket(user, request);
    }

    @Test
    void shouldRejectInternalAdminPrincipalForUserTicketApis() {
        assertThatThrownBy(() -> controller.listTickets(new InternalAdminPrincipal(UUID.randomUUID())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract authenticated user");
    }

    @Test
    void shouldDelegateReplyToServiceWithCurrentUser() {
        User user = user();
        UUID ticketId = UUID.randomUUID();
        SupportTicketReplyRequest request = new SupportTicketReplyRequest("Thanks for the update.");
        SupportTicketDto dto = dto(user.getId());
        when(supportTicketService.reply(user, ticketId, request)).thenReturn(dto);

        ResponseEntity<SupportTicketDto> response =
            controller.reply(new UserPrincipal(user), ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(supportTicketService).reply(user, ticketId, request);
    }

    @Test
    void shouldReturnUnreadStateAndMarkTicketRead() {
        User user = user();
        UUID ticketId = UUID.randomUUID();
        SupportTicketUnreadResponse unread = new SupportTicketUnreadResponse(1, List.of(ticketId));
        when(supportTicketService.getUnreadSummary(user)).thenReturn(unread);
        when(supportTicketService.markRead(user, ticketId)).thenReturn(new SupportTicketUnreadResponse(0, List.of()));

        assertThat(controller.getUnreadSummary(new UserPrincipal(user)).getBody()).isEqualTo(unread);
        assertThat(controller.markRead(new UserPrincipal(user), ticketId).getBody().unreadCount()).isZero();
    }

    @Test
    void shouldReturnTooManyRequestsForSupportRateLimit() {
        SupportTicketRateLimitExceededException exception = new SupportTicketRateLimitExceededException(
            "Too many support ticket create requests",
            Duration.ofSeconds(42)
        );

        ResponseEntity<SupportTicketController.ErrorResponse> response = controller.handleRateLimit(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getBody()).isEqualTo(
            new SupportTicketController.ErrorResponse("SUPPORT_TICKET_RATE_LIMITED", exception.getMessage())
        );
    }

    @Test
    void shouldReturnSanitizedServiceUnavailableForRateLimiterOutage() {
        SupportTicketRateLimiterUnavailableException exception =
            new SupportTicketRateLimiterUnavailableException("Redis unavailable at redis://internal:6379");

        ResponseEntity<SupportTicketController.ErrorResponse> response =
            controller.handleRateLimiterUnavailable(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new SupportTicketController.ErrorResponse(
            "SUPPORT_TICKET_RATE_LIMITER_UNAVAILABLE",
            "Support ticket requests are temporarily unavailable. Please try again shortly."
        ));
        assertThat(response.getBody().message()).doesNotContain("Redis", "internal", "6379");
    }

    private static User user() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .displayName("Customer User")
            .enabled(true)
            .emailVerified(true)
            .build();
        user.setEmail("customer@example.com");
        return user;
    }

    private static SupportTicketDto dto(UUID userId) {
        return new SupportTicketDto(
            UUID.randomUUID(),
            "MM-0126",
            SupportTicketStatus.OPEN,
            SupportTicketPriority.MEDIUM,
            "Subject",
            LocalDateTime.of(2026, 6, 3, 12, 0),
            LocalDateTime.of(2026, 6, 3, 12, 0),
            null,
            0,
            null,
            List.of()
        );
    }
}
