package com.marketmono.controller;

import com.marketmono.dto.support.CreateSupportTicketRequest;
import com.marketmono.dto.support.SupportTicketDto;
import com.marketmono.dto.support.SupportTicketReplyRequest;
import com.marketmono.dto.support.SupportTicketUnreadResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.service.support.SupportTicketRateLimitExceededException;
import com.marketmono.service.support.SupportTicketRateLimiterUnavailableException;
import com.marketmono.service.support.SupportTicketService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support/tickets")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @PostMapping
    public ResponseEntity<SupportTicketDto> createTicket(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateSupportTicketRequest request) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        log.info("POST /api/v1/support/tickets - userId: {}", user.getId());
        SupportTicketDto created = supportTicketService.createTicket(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketDto>> listTickets(@AuthenticationPrincipal UserDetails userDetails) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(supportTicketService.listTickets(user));
    }

    @GetMapping("/unread-summary")
    public ResponseEntity<SupportTicketUnreadResponse> getUnreadSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(supportTicketService.getUnreadSummary(user));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<SupportTicketDto> getTicket(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ticketId) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(supportTicketService.getTicket(user, ticketId));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<SupportTicketDto> reply(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ticketId,
            @Valid @RequestBody SupportTicketReplyRequest request) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        log.info("POST /api/v1/support/tickets/{}/messages - userId: {}", ticketId, user.getId());
        return ResponseEntity.ok(supportTicketService.reply(user, ticketId, request));
    }

    @PostMapping("/{ticketId}/read")
    public ResponseEntity<SupportTicketUnreadResponse> markRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID ticketId) {
        User user = PrincipalUtils.requireAuthenticatedUser(userDetails);
        return ResponseEntity.ok(supportTicketService.markRead(user, ticketId));
    }

    @ExceptionHandler(SupportTicketService.SupportTicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SupportTicketService.SupportTicketNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("SUPPORT_TICKET_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({
        SupportTicketService.SupportTicketValidationException.class,
        SupportTicketService.SupportTicketClosedException.class
    })
    public ResponseEntity<ErrorResponse> handleValidation(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("SUPPORT_TICKET_INVALID", exception.getMessage()));
    }

    @ExceptionHandler(SupportTicketRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(SupportTicketRateLimitExceededException exception) {
        long retryAfterSeconds = Math.max(1, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
            .body(new ErrorResponse("SUPPORT_TICKET_RATE_LIMITED", exception.getMessage()));
    }

    @ExceptionHandler(SupportTicketRateLimiterUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateLimiterUnavailable(
            SupportTicketRateLimiterUnavailableException exception) {
        log.warn("Support ticket rate limiter unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(
                "SUPPORT_TICKET_RATE_LIMITER_UNAVAILABLE",
                "Support ticket requests are temporarily unavailable. Please try again shortly."
            ));
    }

    public record ErrorResponse(String code, String message) {}
}
