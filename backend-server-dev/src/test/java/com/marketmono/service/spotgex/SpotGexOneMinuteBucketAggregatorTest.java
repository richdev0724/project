package com.marketmono.service.spotgex;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexOneMinuteBucketAggregatorTest {

    private static final long BUCKET_START_MS = 1_781_198_340_000L;

    private final SpotGexOneMinuteBucketAggregator aggregator = new SpotGexOneMinuteBucketAggregator();

    @Test
    void shouldKeepLatestEventForTickerExpiryStrikeInsideOneMinuteBucket() {
        aggregator.accept(event(1_781_198_341_000L, "100.0000", "-20.0000", "725.1000"));
        aggregator.accept(event(1_781_198_376_000L, "250.0000", "-75.0000", "725.6750"));
        aggregator.accept(event(1_781_198_350_000L, "999.0000", "-999.0000", "725.5000"));

        List<SpotGexStreamBucket> buckets = aggregator.drainReadyBuckets(BUCKET_START_MS + 60_000L);

        assertThat(buckets).hasSize(1);
        SpotGexStreamBucket bucket = buckets.getFirst();
        assertThat(bucket.bucketKey()).isEqualTo("SPY|2026-06-11|72500|1781198340000");
        assertThat(bucket.latestTimestampMs()).isEqualTo(1_781_198_376_000L);
        assertThat(bucket.underlyingPrice()).isEqualByComparingTo("725.6750");
        assertThat(bucket.callGammaOi()).isEqualByComparingTo("250.0000");
        assertThat(bucket.putGammaOi()).isEqualByComparingTo("-75.0000");
        assertThat(bucket.netGammaOi()).isEqualByComparingTo("175.0000");
    }

    @Test
    void shouldNotFlushOpenMinuteBeforeItCloses() {
        aggregator.accept(event(1_781_198_376_000L, "250.0000", "-75.0000", "725.6750"));

        List<SpotGexStreamBucket> buckets = aggregator.drainReadyBuckets(BUCKET_START_MS + 59_999L);

        assertThat(buckets).isEmpty();
    }

    private static SpotGexStreamEvent event(
        long timestampMs,
        String callGammaOi,
        String putGammaOi,
        String underlyingPrice
    ) {
        return SpotGexStreamEvent.builder()
            .ticker("SPY")
            .expiry(LocalDate.of(2026, 6, 11))
            .strikePrice(new BigDecimal("725"))
            .strikePriceCents(72_500)
            .timestampMs(timestampMs)
            .bucketStartMs(timestampMs - (timestampMs % 60_000L))
            .ingestedAtMs(timestampMs + 200L)
            .underlyingPrice(new BigDecimal(underlyingPrice))
            .callGammaOi(new BigDecimal(callGammaOi))
            .putGammaOi(new BigDecimal(putGammaOi))
            .source("options_feed_ws")
            .rawChannelName("gex_strike_expiry:SPY")
            .build();
    }
}
