package com.marketmono.service.spotgex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record SpotGexStreamEvent(
    String ticker,
    LocalDate expiry,
    BigDecimal strikePrice,
    int strikePriceCents,
    long timestampMs,
    long bucketStartMs,
    long ingestedAtMs,
    BigDecimal underlyingPrice,
    BigDecimal callDeltaOi,
    BigDecimal putDeltaOi,
    BigDecimal callGammaOi,
    BigDecimal putGammaOi,
    BigDecimal callCharmOi,
    BigDecimal putCharmOi,
    BigDecimal callVannaOi,
    BigDecimal putVannaOi,
    BigDecimal callDeltaVol,
    BigDecimal putDeltaVol,
    BigDecimal callGammaVol,
    BigDecimal putGammaVol,
    BigDecimal callCharmVol,
    BigDecimal putCharmVol,
    BigDecimal callVannaVol,
    BigDecimal putVannaVol,
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

    public SpotGexStreamEvent {
        ticker = ticker == null ? null : ticker.trim().toUpperCase(Locale.ROOT);
        source = source == null ? SpotGexWebSocketPayloadParser.SOURCE_OPTIONS_FEED_WS : source;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String rawKey() {
        return ticker + "|" + expiry + "|" + strikePriceCents + "|" + timestampMs;
    }

    public String bucketKey() {
        return ticker + "|" + expiry + "|" + strikePriceCents + "|" + bucketStartMs;
    }

    public static final class Builder {
        private String ticker;
        private LocalDate expiry;
        private BigDecimal strikePrice;
        private int strikePriceCents;
        private long timestampMs;
        private long bucketStartMs;
        private long ingestedAtMs;
        private BigDecimal underlyingPrice;
        private BigDecimal callDeltaOi;
        private BigDecimal putDeltaOi;
        private BigDecimal callGammaOi;
        private BigDecimal putGammaOi;
        private BigDecimal callCharmOi;
        private BigDecimal putCharmOi;
        private BigDecimal callVannaOi;
        private BigDecimal putVannaOi;
        private BigDecimal callDeltaVol;
        private BigDecimal putDeltaVol;
        private BigDecimal callGammaVol;
        private BigDecimal putGammaVol;
        private BigDecimal callCharmVol;
        private BigDecimal putCharmVol;
        private BigDecimal callVannaVol;
        private BigDecimal putVannaVol;
        private BigDecimal callGammaAsk;
        private BigDecimal callGammaBid;
        private BigDecimal putGammaAsk;
        private BigDecimal putGammaBid;
        private BigDecimal callCharmAsk;
        private BigDecimal callCharmBid;
        private BigDecimal putCharmAsk;
        private BigDecimal putCharmBid;
        private BigDecimal callVannaAsk;
        private BigDecimal callVannaBid;
        private BigDecimal putVannaAsk;
        private BigDecimal putVannaBid;
        private String source;
        private String rawChannelName;

        public Builder ticker(String ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder expiry(LocalDate expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder strikePrice(BigDecimal strikePrice) {
            this.strikePrice = strikePrice;
            return this;
        }

        public Builder strikePriceCents(int strikePriceCents) {
            this.strikePriceCents = strikePriceCents;
            return this;
        }

        public Builder timestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
            return this;
        }

        public Builder bucketStartMs(long bucketStartMs) {
            this.bucketStartMs = bucketStartMs;
            return this;
        }

        public Builder ingestedAtMs(long ingestedAtMs) {
            this.ingestedAtMs = ingestedAtMs;
            return this;
        }

        public Builder underlyingPrice(BigDecimal underlyingPrice) {
            this.underlyingPrice = underlyingPrice;
            return this;
        }

        public Builder callDeltaOi(BigDecimal callDeltaOi) {
            this.callDeltaOi = callDeltaOi;
            return this;
        }

        public Builder putDeltaOi(BigDecimal putDeltaOi) {
            this.putDeltaOi = putDeltaOi;
            return this;
        }

        public Builder callGammaOi(BigDecimal callGammaOi) {
            this.callGammaOi = callGammaOi;
            return this;
        }

        public Builder putGammaOi(BigDecimal putGammaOi) {
            this.putGammaOi = putGammaOi;
            return this;
        }

        public Builder callCharmOi(BigDecimal callCharmOi) {
            this.callCharmOi = callCharmOi;
            return this;
        }

        public Builder putCharmOi(BigDecimal putCharmOi) {
            this.putCharmOi = putCharmOi;
            return this;
        }

        public Builder callVannaOi(BigDecimal callVannaOi) {
            this.callVannaOi = callVannaOi;
            return this;
        }

        public Builder putVannaOi(BigDecimal putVannaOi) {
            this.putVannaOi = putVannaOi;
            return this;
        }

        public Builder callDeltaVol(BigDecimal callDeltaVol) {
            this.callDeltaVol = callDeltaVol;
            return this;
        }

        public Builder putDeltaVol(BigDecimal putDeltaVol) {
            this.putDeltaVol = putDeltaVol;
            return this;
        }

        public Builder callGammaVol(BigDecimal callGammaVol) {
            this.callGammaVol = callGammaVol;
            return this;
        }

        public Builder putGammaVol(BigDecimal putGammaVol) {
            this.putGammaVol = putGammaVol;
            return this;
        }

        public Builder callCharmVol(BigDecimal callCharmVol) {
            this.callCharmVol = callCharmVol;
            return this;
        }

        public Builder putCharmVol(BigDecimal putCharmVol) {
            this.putCharmVol = putCharmVol;
            return this;
        }

        public Builder callVannaVol(BigDecimal callVannaVol) {
            this.callVannaVol = callVannaVol;
            return this;
        }

        public Builder putVannaVol(BigDecimal putVannaVol) {
            this.putVannaVol = putVannaVol;
            return this;
        }

        public Builder callGammaAsk(BigDecimal callGammaAsk) {
            this.callGammaAsk = callGammaAsk;
            return this;
        }

        public Builder callGammaBid(BigDecimal callGammaBid) {
            this.callGammaBid = callGammaBid;
            return this;
        }

        public Builder putGammaAsk(BigDecimal putGammaAsk) {
            this.putGammaAsk = putGammaAsk;
            return this;
        }

        public Builder putGammaBid(BigDecimal putGammaBid) {
            this.putGammaBid = putGammaBid;
            return this;
        }

        public Builder callCharmAsk(BigDecimal callCharmAsk) {
            this.callCharmAsk = callCharmAsk;
            return this;
        }

        public Builder callCharmBid(BigDecimal callCharmBid) {
            this.callCharmBid = callCharmBid;
            return this;
        }

        public Builder putCharmAsk(BigDecimal putCharmAsk) {
            this.putCharmAsk = putCharmAsk;
            return this;
        }

        public Builder putCharmBid(BigDecimal putCharmBid) {
            this.putCharmBid = putCharmBid;
            return this;
        }

        public Builder callVannaAsk(BigDecimal callVannaAsk) {
            this.callVannaAsk = callVannaAsk;
            return this;
        }

        public Builder callVannaBid(BigDecimal callVannaBid) {
            this.callVannaBid = callVannaBid;
            return this;
        }

        public Builder putVannaAsk(BigDecimal putVannaAsk) {
            this.putVannaAsk = putVannaAsk;
            return this;
        }

        public Builder putVannaBid(BigDecimal putVannaBid) {
            this.putVannaBid = putVannaBid;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder rawChannelName(String rawChannelName) {
            this.rawChannelName = rawChannelName;
            return this;
        }

        public SpotGexStreamEvent build() {
            return new SpotGexStreamEvent(
                ticker,
                expiry,
                strikePrice,
                strikePriceCents,
                timestampMs,
                bucketStartMs,
                ingestedAtMs,
                underlyingPrice,
                callDeltaOi,
                putDeltaOi,
                callGammaOi,
                putGammaOi,
                callCharmOi,
                putCharmOi,
                callVannaOi,
                putVannaOi,
                callDeltaVol,
                putDeltaVol,
                callGammaVol,
                putGammaVol,
                callCharmVol,
                putCharmVol,
                callVannaVol,
                putVannaVol,
                callGammaAsk,
                callGammaBid,
                putGammaAsk,
                putGammaBid,
                callCharmAsk,
                callCharmBid,
                putCharmAsk,
                putCharmBid,
                callVannaAsk,
                callVannaBid,
                putVannaAsk,
                putVannaBid,
                source,
                rawChannelName
            );
        }
    }
}
