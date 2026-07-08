package com.marketmono.service.spotgex;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpotGexStreamBucket(
    String ticker,
    LocalDate expiry,
    BigDecimal strikePrice,
    int strikePriceCents,
    long bucketStartMs,
    long latestTimestampMs,
    long ingestedAtMs,
    BigDecimal underlyingPrice,
    BigDecimal callDeltaOi,
    BigDecimal putDeltaOi,
    BigDecimal netDeltaOi,
    BigDecimal callGammaOi,
    BigDecimal putGammaOi,
    BigDecimal netGammaOi,
    BigDecimal callCharmOi,
    BigDecimal putCharmOi,
    BigDecimal netCharmOi,
    BigDecimal callVannaOi,
    BigDecimal putVannaOi,
    BigDecimal netVannaOi,
    BigDecimal callDeltaVol,
    BigDecimal putDeltaVol,
    BigDecimal netDeltaVol,
    BigDecimal callGammaVol,
    BigDecimal putGammaVol,
    BigDecimal netGammaVol,
    BigDecimal callCharmVol,
    BigDecimal putCharmVol,
    BigDecimal netCharmVol,
    BigDecimal callVannaVol,
    BigDecimal putVannaVol,
    BigDecimal netVannaVol,
    BigDecimal callGammaAsk,
    BigDecimal callGammaBid,
    BigDecimal putGammaAsk,
    BigDecimal putGammaBid,
    BigDecimal callCharmAsk,
    BigDecimal callCharmBid,
    BigDecimal putCharmAsk,
    BigDecimal putCharmBid,
    BigDecimal callVannaAsk,
    BigDecimal callVannaBid,
    BigDecimal putVannaAsk,
    BigDecimal putVannaBid,
    String source,
    String rawChannelName
) {

    static SpotGexStreamBucket fromLatestEvent(SpotGexStreamEvent event) {
        return new SpotGexStreamBucket(
            event.ticker(),
            event.expiry(),
            event.strikePrice(),
            event.strikePriceCents(),
            event.bucketStartMs(),
            event.timestampMs(),
            event.ingestedAtMs(),
            event.underlyingPrice(),
            event.callDeltaOi(),
            event.putDeltaOi(),
            add(event.callDeltaOi(), event.putDeltaOi()),
            event.callGammaOi(),
            event.putGammaOi(),
            add(event.callGammaOi(), event.putGammaOi()),
            event.callCharmOi(),
            event.putCharmOi(),
            add(event.callCharmOi(), event.putCharmOi()),
            event.callVannaOi(),
            event.putVannaOi(),
            add(event.callVannaOi(), event.putVannaOi()),
            event.callDeltaVol(),
            event.putDeltaVol(),
            add(event.callDeltaVol(), event.putDeltaVol()),
            event.callGammaVol(),
            event.putGammaVol(),
            add(event.callGammaVol(), event.putGammaVol()),
            event.callCharmVol(),
            event.putCharmVol(),
            add(event.callCharmVol(), event.putCharmVol()),
            event.callVannaVol(),
            event.putVannaVol(),
            add(event.callVannaVol(), event.putVannaVol()),
            event.callGammaAsk(),
            event.callGammaBid(),
            event.putGammaAsk(),
            event.putGammaBid(),
            event.callCharmAsk(),
            event.callCharmBid(),
            event.putCharmAsk(),
            event.putCharmBid(),
            event.callVannaAsk(),
            event.callVannaBid(),
            event.putVannaAsk(),
            event.putVannaBid(),
            event.source(),
            event.rawChannelName()
        );
    }

    public String bucketKey() {
        return ticker + "|" + expiry + "|" + strikePriceCents + "|" + bucketStartMs;
    }

    private static BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }
}
