package com.marketmono.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpotGexStreamBucketRepository {

    record StreamBucketStatusProjection(
        String ticker,
        Instant latestBucketStart,
        Long rowsToday,
        Long rowsLatestSession,
        Long currentRows
    ) {}

    Optional<Instant> findLatestBucketStartByTicker(String ticker);

    List<StreamBucketStatusProjection> findStatusByTicker(List<String> tickers);

    List<SpotGexExposureRepository.SpotGexHistoryProjection> findSnapshot(
        String ticker,
        Instant bucketStart,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    );

    List<Instant> findReplaySessionBucketStarts(
        String ticker,
        Instant openAt,
        Instant closeAt
    );

    List<SpotGexExposureRepository.SpotGexHistoryProjection> findReplaySnapshot(
        String ticker,
        Instant bucketStart,
        Instant sessionOpenAt,
        LocalDate expiry,
        BigDecimal minStrike,
        BigDecimal maxStrike
    );

    default List<SpotGexExposureRepository.SpotGexHistoryProjection> findHistory(
        String ticker,
        Instant since,
        String bucketInterval,
        LocalDate expiry,
        BigDecimal strike
    ) {
        return findHistory(ticker, since, bucketInterval, expiry, strike, null, null);
    }

    List<SpotGexExposureRepository.SpotGexHistoryProjection> findHistory(
        String ticker,
        Instant since,
        String bucketInterval,
        LocalDate expiry,
        BigDecimal strike,
        BigDecimal minStrike,
        BigDecimal maxStrike
    );
}
