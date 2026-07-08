package com.marketmono.service.spotgex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexWebSocketPayloadParserTest {

    private static final Instant INGESTED_AT = Instant.parse("2026-06-11T14:40:01Z");

    private final SpotGexWebSocketPayloadParser parser = new SpotGexWebSocketPayloadParser(new ObjectMapper());

    @Test
    void shouldParseCapturedSpotGexStrikeExpiryFrame() {
        Optional<SpotGexStreamEvent> parsed = parser.parseEnvelope(capturedSpyFrame(), INGESTED_AT);

        assertThat(parsed).hasValueSatisfying(event -> {
            assertThat(event.ticker()).isEqualTo("SPY");
            assertThat(event.expiry()).isEqualTo(LocalDate.of(2026, 6, 11));
            assertThat(event.strikePrice()).isEqualByComparingTo("725");
            assertThat(event.strikePriceCents()).isEqualTo(72_500);
            assertThat(event.timestampMs()).isEqualTo(1_781_198_376_000L);
            assertThat(event.bucketStartMs()).isEqualTo(1_781_198_340_000L);
            assertThat(event.ingestedAtMs()).isEqualTo(INGESTED_AT.toEpochMilli());
            assertThat(event.underlyingPrice()).isEqualByComparingTo("725.675");
            assertThat(event.callGammaOi()).isEqualByComparingTo("61121163.0323106");
            assertThat(event.putGammaOi()).isEqualByComparingTo("-348912158.9329215");
            assertThat(event.callGammaBid()).isEqualByComparingTo("678133839.090257");
            assertThat(event.callGammaAsk()).isEqualByComparingTo("-623192050.862451");
            assertThat(event.putGammaBid()).isEqualByComparingTo("7987594702.8856");
            assertThat(event.putGammaAsk()).isEqualByComparingTo("-7556908234.60305");
            assertThat(event.source()).isEqualTo("options_feed_ws");
            assertThat(event.rawChannelName()).isEqualTo("gex_strike_expiry:SPY");
            assertThat(event.rawKey()).isEqualTo("SPY|2026-06-11|72500|1781198376000");
            assertThat(event.bucketKey()).isEqualTo("SPY|2026-06-11|72500|1781198340000");
        });
    }

    @Test
    void shouldSkipJoinAcknowledgementFrames() {
        String acknowledgement = "[\"gex_strike_expiry:SPY\",{\"response\":{},\"status\":\"ok\"}]";

        Optional<SpotGexStreamEvent> parsed = parser.parseEnvelope(acknowledgement, INGESTED_AT);

        assertThat(parsed).isEmpty();
    }

    @Test
    void shouldRejectTickerThatDoesNotMatchChannelSuffix() {
        String mismatchedFrame = capturedSpyFrame().replace("\"gex_strike_expiry:SPY\"", "\"gex_strike_expiry:QQQ\"");

        Optional<SpotGexStreamEvent> parsed = parser.parseEnvelope(mismatchedFrame, INGESTED_AT);

        assertThat(parsed).isEmpty();
    }

    @Test
    void shouldPreserveOmittedStreamNumericFieldsAsUnavailableAndIgnoreDelta() {
        Optional<SpotGexStreamEvent> parsed = parser.parseEnvelope(frameWithOnlyCallGammaBidAsk(), INGESTED_AT);

        assertThat(parsed).hasValueSatisfying(event -> {
            assertThat(event.callDeltaOi()).isNull();
            assertThat(event.putDeltaOi()).isNull();
            assertThat(event.callDeltaVol()).isNull();
            assertThat(event.putDeltaVol()).isNull();
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

    private static String capturedSpyFrame() {
        return """
            [
              "gex_strike_expiry:SPY",
              {
                "ticker": "SPY",
                "timestamp": 1781198376000,
                "call_delta_oi": "0",
                "put_delta_oi": "0",
                "call_gamma_oi": "61121163.0323106",
                "put_gamma_oi": "-348912158.9329215",
                "call_charm_oi": "401780029.0555459",
                "put_charm_oi": "-2940432865.187607",
                "call_vanna_oi": "-40261.12147260641",
                "put_vanna_oi": "267923.1906932312",
                "call_delta_vol": "0",
                "put_delta_vol": "0",
                "call_gamma_vol": "1416716256.20009",
                "put_gamma_vol": "-16531870759.35712",
                "call_charm_vol": "9312785790.39203",
                "put_charm_vol": "-139321186892.7535",
                "call_vanna_vol": "-933205.169098887",
                "put_vanna_vol": "12694517.65602304",
                "strike": "725",
                "expiry": "2026-06-11",
                "price": "725.675",
                "call_gamma_ask_vol": "-623192050.862451",
                "call_gamma_bid_vol": "678133839.090257",
                "put_gamma_ask_vol": "-7556908234.60305",
                "put_gamma_bid_vol": "7987594702.8856",
                "call_charm_ask_vol": "-4096553597.488631",
                "call_charm_bid_vol": "4457713499.810226",
                "put_charm_ask_vol": "-63685316671.6543",
                "put_charm_bid_vol": "67314896821.0037",
                "call_vanna_ask_vol": "410502.8375731706",
                "call_vanna_bid_vol": "-446693.5430509537",
                "put_vanna_ask_vol": "5802810.00290405",
                "put_vanna_bid_vol": "-6133526.17262297"
              }
            ]
            """;
    }

    private static String frameWithOnlyCallGammaBidAsk() {
        return """
            [
              "gex_strike_expiry:SPY",
              {
                "ticker": "SPY",
                "timestamp": 1781198376000,
                "strike": "725",
                "expiry": "2026-06-11",
                "price": "725.675",
                "call_delta_oi": "0",
                "put_delta_oi": "0",
                "call_delta_vol": "0",
                "put_delta_vol": "0",
                "call_gamma_ask_vol": "-100.25",
                "call_gamma_bid_vol": "300.50"
              }
            ]
            """;
    }
}
