package com.marketmono.controller;

import com.marketmono.exception.WhopWebhookVerificationException;
import com.marketmono.service.WhopWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for handling Whop webhook deliveries.
 * Endpoints are public but protected by Standard Webhooks signature verification.
 */
@RestController
@RequestMapping("/api/v1/whop")
@RequiredArgsConstructor
@Slf4j
public class WhopWebhookController {

    private final WhopWebhookService webhookService;

    @PostMapping("/webhooks")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature) {
        try {
            webhookService.handleWebhook(webhookId, webhookTimestamp, webhookSignature, payload);
            return ResponseEntity.ok("Webhook processed");
        } catch (WhopWebhookVerificationException e) {
            log.warn("Whop webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid signature");
        } catch (Exception e) {
            log.error("Whop webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Webhook processing error");
        }
    }
}
