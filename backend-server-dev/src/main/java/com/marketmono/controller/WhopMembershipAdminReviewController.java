package com.marketmono.controller;

import com.marketmono.dto.whop.WhopMembershipAttachRequest;
import com.marketmono.dto.whop.WhopMembershipAttachResponse;
import com.marketmono.dto.whop.WhopMembershipResolveWithoutActionRequest;
import com.marketmono.dto.whop.WhopMembershipReviewNoteRequest;
import com.marketmono.dto.whop.WhopMembershipReviewDetailDto;
import com.marketmono.dto.whop.WhopMembershipReviewItemDto;
import com.marketmono.dto.whop.WhopMembershipReviewPageDto;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import com.marketmono.service.WhopMembershipAdminReviewService;
import com.marketmono.util.PrincipalUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/whop/membership-reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_ADMIN')")
public class WhopMembershipAdminReviewController {

    private final WhopMembershipAdminReviewService reviewService;

    @GetMapping
    public ResponseEntity<WhopMembershipReviewPageDto> listReviews(
            @RequestParam(required = false) WhopMembershipCorrelationStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeResolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WhopMembershipReviewItemDto> reviews = reviewService.listReviews(
            status,
            search,
            includeResolved,
            PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
            )
        );
        return ResponseEntity.ok(WhopMembershipReviewPageDto.from(reviews));
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<WhopMembershipReviewDetailDto> getReviewDetail(@PathVariable UUID correlationId) {
        return ResponseEntity.ok(reviewService.getReviewDetail(correlationId));
    }

    @PostMapping("/{correlationId}/attach")
    public ResponseEntity<WhopMembershipAttachResponse> attachMembership(
            @PathVariable UUID correlationId,
            @Valid @RequestBody WhopMembershipAttachRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(reviewService.attachMembership(
            correlationId,
            request,
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            clientIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/{correlationId}/resolve-without-action")
    public ResponseEntity<WhopMembershipReviewDetailDto> resolveWithoutAction(
            @PathVariable UUID correlationId,
            @Valid @RequestBody WhopMembershipResolveWithoutActionRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(reviewService.resolveWithoutAction(
            correlationId,
            request.reviewNote(),
            PrincipalUtils.requireInternalAdminActorId(userDetails),
            clientIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        ));
    }

    @PostMapping("/{correlationId}/review-note")
    public ResponseEntity<WhopMembershipReviewDetailDto> updateReviewNote(
            @PathVariable UUID correlationId,
            @Valid @RequestBody WhopMembershipReviewNoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PrincipalUtils.requireInternalAdminActorId(userDetails);
        return ResponseEntity.ok(reviewService.updateReviewNote(correlationId, request.reviewNote()));
    }

    @ExceptionHandler(WhopMembershipAdminReviewService.WhopMembershipReviewNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            WhopMembershipAdminReviewService.WhopMembershipReviewNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("WHOP_MEMBERSHIP_REVIEW_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WhopMembershipAdminReviewService.WhopMembershipReviewConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            WhopMembershipAdminReviewService.WhopMembershipReviewConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("WHOP_MEMBERSHIP_REVIEW_CONFLICT", ex.getMessage()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public record ErrorResponse(String code, String message) {}
}
