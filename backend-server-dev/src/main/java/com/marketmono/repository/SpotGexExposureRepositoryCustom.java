package com.marketmono.repository;

import com.marketmono.entity.SpotGexExposure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface SpotGexExposureRepositoryCustom {

    List<SpotGexExposure> findSnapshot(
        String ticker,
        Instant collectedAt,
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

    List<SpotGexExposureRepository.SpotGexStatusProjection> findCollectionStatus(List<String> tickers);
}
