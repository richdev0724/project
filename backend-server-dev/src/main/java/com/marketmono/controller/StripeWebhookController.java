package com.marketmono.controller;

import com.marketmono.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling Stripe webhook events.
 * Endpoints are public but protected by Stripe signature verification.
 */
@RestController
@RequestMapping("/api/v1/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {
    
    private final StripeWebhookService webhookService;
    
    /**
     * Handle Stripe webhook events.
     * Protected by signature verification - no authentication required.
     * 
     * @param payload The raw JSON payload from Stripe
     * @param signatureHeader The Stripe-Signature header for verification
     * @return 200 OK if processed successfully
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {
        
        try {
            webhookService.handleWebhook(payload, signatureHeader);
            return ResponseEntity.ok("Webhook processed");
            
        } catch (SignatureVerificationException e) {
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid signature");
                
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Webhook processing error");
        }
    }
}
