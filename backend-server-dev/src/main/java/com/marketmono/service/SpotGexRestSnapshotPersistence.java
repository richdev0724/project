package com.marketmono.service;

import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.service.spotgex.SpotGexStreamBucket;
import com.marketmono.service.spotgex.SpotGexStreamBucketPersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SpotGexRestSnapshotPersistence {

    private static final String SOURCE = "uw_rest_spot_gex";
    private static final String RAW_CHANNEL_NAME = "rest:expiry-strike";

    private final SpotGexStreamBucketPersistence streamBucketPersistence;

    public SpotGexStreamBucketPersistence.BatchPersistResult persistSnapshot(
            String providerRoot,
            List<SpotExposureByStrikeDto> rows,
            Instant collectedAt) {
        if (rows == null || rows.isEmpty()) {
            return new SpotGexStreamBucketPersistence.BatchPersistResult(0, 0, 0);
        }

        Instant effectiveCollectedAt = collectedAt == null ? Instant.now() : collectedAt;
        long bucketStartMs = effectiveCollectedAt.truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
        long ingestedAtMs = effectiveCollectedAt.toEpochMilli();
        List<SpotGexStreamBucket> buckets = rows.stream()
            .filter(Objects::nonNull)
            .filter(row -> row.expiry() != null && row.strike() != null)
            .map(row -> toBucket(providerRoot, row, bucketStartMs, ingestedAtMs))
            .toList();

        if (buckets.isEmpty()) {
            return new SpotGexStreamBucketPersistence.BatchPersistResult(0, 0, 0);
        }
        return streamBucketPersistence.persistBuckets(buckets);
    }

    private SpotGexStreamBucket toBucket(String providerRoot, SpotExposureByStrikeDto row, long bucketStartMs, long ingestedAtMs) {
        long latestTimestampMs = row.time() != null ? row.time().toInstant().toEpochMilli() : ingestedAtMs;
        return new SpotGexStreamBucket(
            normalizeProviderRoot(providerRoot),
            row.expiry(),
            row.strike(),
            strikePriceCents(row.strike()),
            bucketStartMs,
            latestTimestampMs,
            ingestedAtMs,
            row.price(),
            row.callDeltaOi(),
            row.putDeltaOi(),
            add(row.callDeltaOi(), row.putDeltaOi()),
            row.callGammaOi(),
            row.putGammaOi(),
            add(row.callGammaOi(), row.putGammaOi()),
            row.callCharmOi(),
            row.putCharmOi(),
            add(row.callCharmOi(), row.putCharmOi()),
            row.callVannaOi(),
            row.putVannaOi(),
            add(row.callVannaOi(), row.putVannaOi()),
            row.callDeltaVol(),
            row.putDeltaVol(),
            add(row.callDeltaVol(), row.putDeltaVol()),
            row.callGammaVol(),
            row.putGammaVol(),
            add(row.callGammaVol(), row.putGammaVol()),
            row.callCharmVol(),
            row.putCharmVol(),
            add(row.callCharmVol(), row.putCharmVol()),
            row.callVannaVol(),
            row.putVannaVol(),
            add(row.callVannaVol(), row.putVannaVol()),
            row.callGammaAsk(),
            row.callGammaBid(),
            row.putGammaAsk(),
            row.putGammaBid(),
            row.callCharmAsk(),
            row.callCharmBid(),
            row.putCharmAsk(),
            row.putCharmBid(),
            row.callVannaAsk(),
            row.callVannaBid(),
            row.putVannaAsk(),
            row.putVannaBid(),
            SOURCE,
            RAW_CHANNEL_NAME
        );
    }

    private static String normalizeProviderRoot(String providerRoot) {
        return providerRoot == null ? "" : providerRoot.trim().toUpperCase(Locale.US);
    }

    private static int strikePriceCents(BigDecimal strike) {
        return strike.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
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
