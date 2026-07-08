package com.marketmono.service;

import com.marketmono.config.WhopWebhookProperties;
import com.marketmono.exception.WhopWebhookVerificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies Whop Standard Webhooks signatures using the raw request body.
 */
@Service
public class WhopWebhookVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SYMMETRIC_SIGNATURE_PREFIX = "v1,";
    private static final String SECRET_PREFIX = "whsec_";

    private final WhopWebhookProperties properties;
    private final Clock clock;

    @Autowired
    public WhopWebhookVerifier(WhopWebhookProperties properties) {
        this(properties, Clock.systemUTC());
    }

    WhopWebhookVerifier(WhopWebhookProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void verify(String webhookId, String webhookTimestamp, String webhookSignature, String payload) {
        validateRequiredHeaders(webhookId, webhookTimestamp, webhookSignature);
        long timestamp = parseTimestamp(webhookTimestamp);
        validateTimestamp(timestamp);

        byte[] expectedSignature = sign(webhookId, webhookTimestamp, payload == null ? "" : payload);
        if (!matchesAnySignature(webhookSignature, expectedSignature)) {
            throw new WhopWebhookVerificationException("Invalid Whop webhook signature");
        }
    }

    private void validateRequiredHeaders(String webhookId, String webhookTimestamp, String webhookSignature) {
        if (isBlank(webhookId) || isBlank(webhookTimestamp) || isBlank(webhookSignature)) {
            throw new WhopWebhookVerificationException("Missing Whop webhook signature headers");
        }
        if (webhookId.contains(".")) {
            throw new WhopWebhookVerificationException("Invalid Whop webhook id");
        }
    }

    private long parseTimestamp(String webhookTimestamp) {
        try {
            return Long.parseLong(webhookTimestamp);
        } catch (NumberFormatException e) {
            throw new WhopWebhookVerificationException("Invalid Whop webhook timestamp", e);
        }
    }

    private void validateTimestamp(long timestamp) {
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), clock.instant()).abs();
        if (age.compareTo(properties.getSignatureTolerance()) > 0) {
            throw new WhopWebhookVerificationException("Whop webhook timestamp is outside tolerance");
        }
    }

    private byte[] sign(String webhookId, String webhookTimestamp, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretBytes(), HMAC_SHA256));
            String signedContent = webhookId + "." + webhookTimestamp + "." + payload;
            return mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
        } catch (WhopWebhookVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new WhopWebhookVerificationException("Failed to verify Whop webhook signature", e);
        }
    }

    private byte[] secretBytes() {
        String secret = properties.getSecret();
        if (isBlank(secret)) {
            throw new WhopWebhookVerificationException("Whop webhook secret is not configured");
        }

        String trimmed = secret.trim();
        if (trimmed.startsWith(SECRET_PREFIX)) {
            try {
                return Base64.getDecoder().decode(trimmed.substring(SECRET_PREFIX.length()));
            } catch (IllegalArgumentException e) {
                throw new WhopWebhookVerificationException("Whop webhook secret is not valid base64", e);
            }
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    private boolean matchesAnySignature(String webhookSignature, byte[] expectedSignature) {
        for (String candidate : webhookSignature.trim().split("\\s+")) {
            if (!candidate.startsWith(SYMMETRIC_SIGNATURE_PREFIX)) {
                continue;
            }
            byte[] providedSignature = decodeSignature(candidate.substring(SYMMETRIC_SIGNATURE_PREFIX.length()));
            if (providedSignature.length > 0 && MessageDigest.isEqual(expectedSignature, providedSignature)) {
                return true;
            }
        }
        return false;
    }

    private byte[] decodeSignature(String encodedSignature) {
        try {
            return Base64.getDecoder().decode(encodedSignature);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
