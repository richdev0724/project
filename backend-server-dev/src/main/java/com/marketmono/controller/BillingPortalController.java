package com.marketmono.controller;

import com.marketmono.dto.BillingPortalResponse;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.BillingPortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/portal")
@RequiredArgsConstructor
@Slf4j
public class BillingPortalController {

    private final BillingPortalService billingPortalService;
    private final UserRepository userRepository;

    @PostMapping("/create-session")
    public ResponseEntity<?> createPortalSession(
            Authentication authentication,
            @RequestParam(required = false) String returnUrl) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("User not found: " + authentication.getName()));

        try {
            BillingPortalResponse response = billingPortalService.createPortalSession(user.getId(), returnUrl);
            return ResponseEntity.ok(response);
        } catch (BillingPortalService.BillingPortalUnavailableException e) {
            log.info("Billing portal unavailable for user {}: {}", authentication.getName(), e.getMessage());
            return ResponseEntity.status(409)
                .body(Map.of(
                    "error", "billing_portal_unavailable",
                    "message", "No active billing portal is available for this account"
                ));
        }
    }
}
