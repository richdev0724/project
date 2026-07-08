package com.marketmono.controller;

import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.ReferralSummaryDto;
import com.marketmono.service.ReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Referral-program endpoints for authenticated users.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/api/v1/referrals/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReferralSummaryDto> getMyReferralSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.debug("GET /api/v1/referrals/me - userId={}", principal.getUser().getId());
        return ResponseEntity.ok(referralService.getSummary(principal.getUser()));
    }
}
