package com.marketmono.service.spotgex;

import com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexLiveGexProtobufDecoderTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-06-12T13:30:06Z");

    private final SpotGexLiveGexProtobufDecoder decoder = new SpotGexLiveGexProtobufDecoder();

    @Test
    void shouldDecodeStrikeExpiryRecordAndMapAcceptedInferredNumericFields() throws Exception {
        // Canonical parity evidence: SPY, expiry 2026-06-22, strike 733,
        // timestamp 2026-06-16T20:13:36Z, price 749.97 proved fields 5-10
        // and 13-18 map Gamma, Charm, then Vanna instead of Delta.
        byte[] payload = encodedStrikeExpiryPayload();

        Optional<SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord> decoded = decoder.decode(payload);

        assertThat(decoded).hasValueSatisfying(record -> {
            assertThat(record.ticker()).isEqualTo("INTC");
            assertThat(record.timestampMs()).isEqualTo(1_781_271_004_000L);
            assertThat(record.strike()).isEqualByComparingTo("100");
            assertThat(record.underlyingPrice()).isEqualByComparingTo("117.16");
            assertThat(record.expiry()).isEqualTo(LocalDate.of(2026, 6, 12));
            assertThat(record.numericFields()).containsEntry(5, new BigDecimal("370812.1259996528"));
            assertThat(record.numericFields()).containsEntry(13, new BigDecimal("1186.25"));
            assertThat(record.numericFields()).containsEntry(32, new BigDecimal("-6133526.17262297"));

            SpotGexStreamEvent event = record.toStreamEvent(INGESTED_AT);
            assertThat(event.ticker()).isEqualTo("INTC");
            assertThat(event.expiry()).isEqualTo(LocalDate.of(2026, 6, 12));
            assertThat(event.strikePrice()).isEqualByComparingTo("100");
            assertThat(event.strikePriceCents()).isEqualTo(10_000);
            assertThat(event.bucketStartMs()).isEqualTo(1_781_271_000_000L);
            assertThat(event.underlyingPrice()).isEqualByComparingTo("117.16");
            assertThat(event.callDeltaOi()).isNull();
            assertThat(event.putDeltaOi()).isNull();
            assertThat(event.callGammaOi()).isEqualByComparingTo("370812.1259996528");
            assertThat(event.putGammaOi()).isEqualByComparingTo("-1343963.5227580967");
            assertThat(event.callCharmOi()).isEqualByComparingTo("499851145.23532134");
            assertThat(event.putCharmOi()).isEqualByComparingTo("-4523690314.268826");
            assertThat(event.callVannaOi()).isEqualByComparingTo("-6280.579767690821");
            assertThat(event.putVannaOi()).isEqualByComparingTo("72523.45563041185");
            assertThat(event.callDeltaVol()).isNull();
            assertThat(event.putDeltaVol()).isNull();
            assertThat(event.callGammaVol()).isEqualByComparingTo("1186.25");
            assertThat(event.putGammaVol()).isEqualByComparingTo("-2450.5");
            assertThat(event.callCharmVol()).isEqualByComparingTo("364.75");
            assertThat(event.putCharmVol()).isEqualByComparingTo("-810.125");
            assertThat(event.callVannaVol()).isEqualByComparingTo("44.5");
            assertThat(event.putVannaVol()).isEqualByComparingTo("-91.25");
            assertThat(event.callGammaAsk()).isEqualByComparingTo("-623192050.862451");
            assertThat(event.callGammaBid()).isEqualByComparingTo("678133839.090257");
            assertThat(event.putGammaAsk()).isEqualByComparingTo("-1432.25");
            assertThat(event.putGammaBid()).isEqualByComparingTo("2675.5");
            assertThat(event.callCharmAsk()).isEqualByComparingTo("3901.75");
            assertThat(event.callCharmBid()).isEqualByComparingTo("-4702.125");
            assertThat(event.putCharmAsk()).isEqualByComparingTo("5810.625");
            assertThat(event.putCharmBid()).isEqualByComparingTo("-6604.5");
            assertThat(event.callVannaAsk()).isEqualByComparingTo("7102.25");
            assertThat(event.callVannaBid()).isEqualByComparingTo("-8203.75");
            assertThat(event.putVannaAsk()).isEqualByComparingTo("5802810.00290405");
            assertThat(event.putVannaBid()).isEqualByComparingTo("-6133526.17262297");
            assertThat(event.source()).isEqualTo("options_feed_live_gex");
            assertThat(event.rawChannelName()).isEqualTo("live-gex:gex-strike-expiry-v2");
        });
    }

    @Test
    void shouldPreserveOmittedStreamNumericFieldsAsUnavailable() throws Exception {
        byte[] payload = encodedPayloadWithOnlyCallGammaBidAsk();

        Optional<SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord> decoded = decoder.decode(payload);

        assertThat(decoded).hasValueSatisfying(record -> {
            SpotGexStreamEvent event = record.toStreamEvent(INGESTED_AT);
            assertThat(event.callDeltaOi()).isNull();
            assertThat(event.putDeltaOi()).isNull();
            assertThat(event.callGammaOi()).isNull();
            assertThat(event.putGammaOi()).isNull();
            assertThat(event.callCharmOi()).isNull();
            assertThat(event.putCharmOi()).isNull();
            assertThat(event.callVannaOi()).isNull();
            assertThat(event.putVannaOi()).isNull();
            assertThat(event.callGammaVol()).isNull();
            assertThat(event.putGammaVol()).isNull();
            assertThat(event.callGammaAsk()).isEqualByComparingTo("-100.25");
            assertThat(event.callGammaBid()).isEqualByComparingTo("300.50");
            assertThat(event.putGammaAsk()).isNull();
            assertThat(event.putGammaBid()).isNull();
        });
    }

    @Test
    void shouldRejectPayloadWithoutRequiredIdentityFields() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeString(1, "INTC");
        coded.writeInt64(2, 1_781_271_004_000L);
        coded.flush();

        Optional<SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord> decoded = decoder.decode(output.toByteArray());

        assertThat(decoded).isEmpty();
    }

    @Test
    void shouldRejectMalformedPayload() {
        Optional<SpotGexLiveGexProtobufDecoder.DecodedLiveGexRecord> decoded =
            decoder.decode(new byte[] {(byte) 0xFF, (byte) 0xFF});

        assertThat(decoded).isEmpty();
    }

    private static byte[] encodedStrikeExpiryPayload() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeString(1, "INTC");
        coded.writeInt64(2, 1_781_271_004_000L);
        coded.writeDouble(5, 370_812.1259996528d);
        coded.writeDouble(6, -1_343_963.5227580967d);
        coded.writeDouble(7, 499_851_145.23532134d);
        coded.writeDouble(8, -4_523_690_314.268826d);
        coded.writeDouble(9, -6_280.579767690821d);
        coded.writeDouble(10, 72_523.45563041185d);
        coded.writeDouble(13, 1_186.25d);
        coded.writeDouble(14, -2_450.5d);
        coded.writeDouble(15, 364.75d);
        coded.writeDouble(16, -810.125d);
        coded.writeDouble(17, 44.5d);
        coded.writeDouble(18, -91.25d);
        coded.writeDouble(21, -623_192_050.862451d);
        coded.writeDouble(22, 678_133_839.090257d);
        coded.writeDouble(23, -1_432.25d);
        coded.writeDouble(24, 2_675.5d);
        coded.writeDouble(25, 3_901.75d);
        coded.writeDouble(26, -4_702.125d);
        coded.writeDouble(27, 5_810.625d);
        coded.writeDouble(28, -6_604.5d);
        coded.writeDouble(29, 7_102.25d);
        coded.writeDouble(30, -8_203.75d);
        coded.writeDouble(31, 5_802_810.00290405d);
        coded.writeDouble(32, -6_133_526.17262297d);
        coded.writeString(19, "100");
        coded.writeString(20, "117.16");
        coded.writeString(33, "2026-06-12");
        coded.flush();
        return output.toByteArray();
    }

    private static byte[] encodedPayloadWithOnlyCallGammaBidAsk() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeString(1, "SPY");
        coded.writeInt64(2, 1_781_707_810_000L);
        coded.writeDouble(21, -100.25d);
        coded.writeDouble(22, 300.50d);
        coded.writeString(19, "752");
        coded.writeString(20, "751.32");
        coded.writeString(33, "2026-06-18");
        coded.flush();
        return output.toByteArray();
    }
}
