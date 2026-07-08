package com.marketmono.controller;

import com.marketmono.dto.CheckoutCapabilityDto;
import com.marketmono.service.CheckoutCapabilityService;
import com.marketmono.util.PrincipalUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CheckoutCapabilityController {

    private final CheckoutCapabilityService checkoutCapabilityService;

    @GetMapping("/api/v1/subscriptions/checkout-capability")
    public ResponseEntity<CheckoutCapabilityDto> getCheckoutCapability(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = PrincipalUtils.requireSubscriptionUserId(userDetails);
        return ResponseEntity.ok(checkoutCapabilityService.getCapability(userId));
    }
}
