package com.marketmono.service.spotgex;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class SpotGexLiveGexProtobufDecoder {

    static final String SOURCE_OPTIONS_FEED_LIVE_GEX = "options_feed_live_gex";
    static final String RAW_CHANNEL_NAME = "live-gex:gex-strike-expiry-v2";
    private static final long ONE_MINUTE_MS = 60_000L;

    public Optional<DecodedLiveGexRecord> decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);
            String ticker = null;
            Long timestampMs = null;
            BigDecimal strike = null;
            BigDecimal underlyingPrice = null;
            LocalDate expiry = null;
            Map<Integer, BigDecimal> numericFields = new HashMap<>();

            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int field = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);

                switch (field) {
                    case 1 -> ticker = readStringOrSkip(input, tag, wireType);
                    case 2 -> timestampMs = readInt64OrSkip(input, tag, wireType);
                    case 19 -> strike = readDecimalStringOrSkip(input, tag, wireType);
                    case 20 -> underlyingPrice = readDecimalStringOrSkip(input, tag, wireType);
                    case 33 -> {
                        String expiryText = readStringOrSkip(input, tag, wireType);
                        expiry = expiryText == null ? null : LocalDate.parse(expiryText);
                    }
                    default -> {
                        BigDecimal numeric = readNumericOrSkip(input, tag, wireType);
                        if (numeric != null) {
                            numericFields.put(field, numeric);
                        }
                    }
                }
            }

            if (ticker == null || ticker.isBlank()
                || timestampMs == null
                || strike == null
                || underlyingPrice == null
                || expiry == null) {
                return Optional.empty();
            }

            return Optional.of(new DecodedLiveGexRecord(
                ticker.trim().toUpperCase(Locale.ROOT),
                timestampMs,
                strike,
                underlyingPrice,
                expiry,
                numericFields
            ));
        } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String readStringOrSkip(CodedInputStream input, int tag, int wireType) throws IOException {
        if (wireType != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            input.skipField(tag);
            return null;
        }
        return input.readStringRequireUtf8();
    }

    private Long readInt64OrSkip(CodedInputStream input, int tag, int wireType) throws IOException {
        if (wireType != WireFormat.WIRETYPE_VARINT) {
            input.skipField(tag);
            return null;
        }
        return input.readInt64();
    }

    private BigDecimal readDecimalStringOrSkip(CodedInputStream input, int tag, int wireType) throws IOException {
        String value = readStringOrSkip(input, tag, wireType);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private BigDecimal readNumericOrSkip(CodedInputStream input, int tag, int wireType) throws IOException {
        return switch (wireType) {
            case WireFormat.WIRETYPE_FIXED64 -> BigDecimal.valueOf(input.readDouble());
            case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                String value = input.readStringRequireUtf8();
                yield value.isBlank() ? null : new BigDecimal(value);
            }
            default -> {
                input.skipField(tag);
                yield null;
            }
        };
    }

    public record DecodedLiveGexRecord(
        String ticker,
        long timestampMs,
        BigDecimal strike,
        BigDecimal underlyingPrice,
        LocalDate expiry,
        Map<Integer, BigDecimal> numericFields
    ) {
        public DecodedLiveGexRecord {
            ticker = ticker == null ? null : ticker.trim().toUpperCase(Locale.ROOT);
            numericFields = numericFields == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(numericFields));
        }

        public SpotGexStreamEvent toStreamEvent(Instant ingestedAt) {
            int strikePriceCents = strike.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
            long bucketStartMs = timestampMs - (timestampMs % ONE_MINUTE_MS);
            long ingestedAtMs = ingestedAt == null ? Instant.now().toEpochMilli() : ingestedAt.toEpochMilli();

            return SpotGexStreamEvent.builder()
                .ticker(ticker)
                .expiry(expiry)
                .strikePrice(strike)
                .strikePriceCents(strikePriceCents)
                .timestampMs(timestampMs)
                .bucketStartMs(bucketStartMs)
                .ingestedAtMs(ingestedAtMs)
                .underlyingPrice(underlyingPrice)
                .callGammaOi(numeric(5))
                .putGammaOi(numeric(6))
                .callCharmOi(numeric(7))
                .putCharmOi(numeric(8))
                .callVannaOi(numeric(9))
                .putVannaOi(numeric(10))
                .callGammaVol(numeric(13))
                .putGammaVol(numeric(14))
                .callCharmVol(numeric(15))
                .putCharmVol(numeric(16))
                .callVannaVol(numeric(17))
                .putVannaVol(numeric(18))
                .callGammaAsk(numeric(21))
                .callGammaBid(numeric(22))
                .putGammaAsk(numeric(23))
                .putGammaBid(numeric(24))
                .callCharmAsk(numeric(25))
                .callCharmBid(numeric(26))
                .putCharmAsk(numeric(27))
                .putCharmBid(numeric(28))
                .callVannaAsk(numeric(29))
                .callVannaBid(numeric(30))
                .putVannaAsk(numeric(31))
                .putVannaBid(numeric(32))
                .source(SOURCE_OPTIONS_FEED_LIVE_GEX)
                .rawChannelName(RAW_CHANNEL_NAME)
                .build();
        }

        private BigDecimal numeric(int field) {
            return numericFields.get(field);
        }
    }
}
