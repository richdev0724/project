package com.marketmono.service;

import com.marketmono.config.WhopWebhookProperties;
import com.marketmono.exception.WhopWebhookVerificationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhopWebhookVerifierTest {

    private static final Instant NOW = Instant.parse("2026-06-09T20:30:00Z");
    private static final String PAYLOAD = "{\"type\":\"membership_activated\",\"data\":{\"id\":\"mem_test\"}}";
    private static final byte[] SECRET_BYTES = "marketmono-whop-test-secret".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(SECRET_BYTES);

    private final WhopWebhookVerifier verifier = new WhopWebhookVerifier(
        new WhopWebhookProperties(SECRET, Duration.ofMinutes(5)),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void shouldAcceptValidStandardWebhookSignature() {
        String webhookId = "msg_valid";
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = sign(webhookId, timestamp, PAYLOAD);

        assertThatCode(() -> verifier.verify(webhookId, timestamp, signature, PAYLOAD))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectInvalidSignature() {
        String timestamp = String.valueOf(NOW.getEpochSecond());

        assertThatThrownBy(() -> verifier.verify("msg_invalid", timestamp, "v1,not-valid", PAYLOAD))
            .isInstanceOf(WhopWebhookVerificationException.class)
            .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectExpiredTimestamp() {
        String webhookId = "msg_old";
        String timestamp = String.valueOf(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        String signature = sign(webhookId, timestamp, PAYLOAD);

        assertThatThrownBy(() -> verifier.verify(webhookId, timestamp, signature, PAYLOAD))
            .isInstanceOf(WhopWebhookVerificationException.class)
            .hasMessageContaining("timestamp");
    }

    @Test
    void shouldRejectMissingSecret() {
        WhopWebhookVerifier missingSecretVerifier = new WhopWebhookVerifier(
            new WhopWebhookProperties("", Duration.ofMinutes(5)),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> missingSecretVerifier.verify(
            "msg_valid",
            String.valueOf(NOW.getEpochSecond()),
            "v1,signature",
            PAYLOAD
        ))
            .isInstanceOf(WhopWebhookVerificationException.class)
            .hasMessageContaining("secret");
    }

    private String sign(String webhookId, String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET_BYTES, "HmacSHA256"));
            String signedContent = webhookId + "." + timestamp + "." + payload;
            String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8))
            );
            return "v1," + signature;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
