package com.marketmono.service.spotgex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SpotGexWebSocketPayloadParser {

    static final String SOURCE_OPTIONS_FEED_WS = "options_feed_ws";
    private static final String CHANNEL_PREFIX = "gex_strike_expiry:";
    private static final long ONE_MINUTE_MS = 60_000L;

    private final ObjectMapper objectMapper;

    public Optional<SpotGexStreamEvent> parseEnvelope(String message, Instant ingestedAt) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(message);
            if (!root.isArray() || root.size() < 2) {
                return Optional.empty();
            }

            String channel = root.get(0).asText("");
            if (!channel.startsWith(CHANNEL_PREFIX)) {
                return Optional.empty();
            }

            JsonNode payload = root.get(1);
            if (!payload.isObject() || isControlFrame(payload)) {
                return Optional.empty();
            }

            String channelTicker = normalizeTicker(channel.substring(CHANNEL_PREFIX.length()));
            String payloadTicker = normalizeTicker(requiredText(payload, "ticker"));
            if (!channelTicker.equals(payloadTicker)) {
                return Optional.empty();
            }

            long timestampMs = requiredLong(payload, "timestamp");
            BigDecimal strike = requiredDecimal(payload, "strike");
            int strikePriceCents = strike.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
            long bucketStartMs = timestampMs - (timestampMs % ONE_MINUTE_MS);

            return Optional.of(SpotGexStreamEvent.builder()
                .ticker(payloadTicker)
                .expiry(LocalDate.parse(requiredText(payload, "expiry")))
                .strikePrice(strike)
                .strikePriceCents(strikePriceCents)
                .timestampMs(timestampMs)
                .bucketStartMs(bucketStartMs)
                .ingestedAtMs(ingestedAt == null ? Instant.now().toEpochMilli() : ingestedAt.toEpochMilli())
                .underlyingPrice(requiredDecimal(payload, "price"))
                // Stream delta is documented as non-meaningful zero; leave it empty for REST enrichment.
                .callGammaOi(numericOrNull(payload, "call_gamma_oi"))
                .putGammaOi(numericOrNull(payload, "put_gamma_oi"))
                .callCharmOi(numericOrNull(payload, "call_charm_oi"))
                .putCharmOi(numericOrNull(payload, "put_charm_oi"))
                .callVannaOi(numericOrNull(payload, "call_vanna_oi"))
                .putVannaOi(numericOrNull(payload, "put_vanna_oi"))
                .callGammaVol(numericOrNull(payload, "call_gamma_vol"))
                .putGammaVol(numericOrNull(payload, "put_gamma_vol"))
                .callCharmVol(numericOrNull(payload, "call_charm_vol"))
                .putCharmVol(numericOrNull(payload, "put_charm_vol"))
                .callVannaVol(numericOrNull(payload, "call_vanna_vol"))
                .putVannaVol(numericOrNull(payload, "put_vanna_vol"))
                .callGammaAsk(numericOrNull(payload, "call_gamma_ask_vol", "call_gamma_ask"))
                .callGammaBid(numericOrNull(payload, "call_gamma_bid_vol", "call_gamma_bid"))
                .putGammaAsk(numericOrNull(payload, "put_gamma_ask_vol", "put_gamma_ask"))
                .putGammaBid(numericOrNull(payload, "put_gamma_bid_vol", "put_gamma_bid"))
                .callCharmAsk(numericOrNull(payload, "call_charm_ask_vol", "call_charm_ask"))
                .callCharmBid(numericOrNull(payload, "call_charm_bid_vol", "call_charm_bid"))
                .putCharmAsk(numericOrNull(payload, "put_charm_ask_vol", "put_charm_ask"))
                .putCharmBid(numericOrNull(payload, "put_charm_bid_vol", "put_charm_bid"))
                .callVannaAsk(numericOrNull(payload, "call_vanna_ask_vol", "call_vanna_ask"))
                .callVannaBid(numericOrNull(payload, "call_vanna_bid_vol", "call_vanna_bid"))
                .putVannaAsk(numericOrNull(payload, "put_vanna_ask_vol", "put_vanna_ask"))
                .putVannaBid(numericOrNull(payload, "put_vanna_bid_vol", "put_vanna_bid"))
                .source(SOURCE_OPTIONS_FEED_WS)
                .rawChannelName(channel)
                .build());
        } catch (JsonProcessingException | IllegalArgumentException | ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private boolean isControlFrame(JsonNode payload) {
        return payload.has("status") && payload.has("response") && !payload.has("timestamp");
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private String requiredText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return node.asText();
    }

    private long requiredLong(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        return Long.parseLong(node.asText());
    }

    private BigDecimal requiredDecimal(JsonNode payload, String field) {
        BigDecimal value = decimal(payload, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private BigDecimal decimal(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode node = payload.get(field);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                return new BigDecimal(node.asText());
            }
        }
        return null;
    }

    private BigDecimal numericOrNull(JsonNode payload, String... fields) {
        try {
            return decimal(payload, fields);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
