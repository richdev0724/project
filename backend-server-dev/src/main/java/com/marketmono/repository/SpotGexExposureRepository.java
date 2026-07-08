package com.marketmono.repository;

import com.marketmono.entity.SpotGexExposure;
import com.marketmono.entity.SpotGexExposureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpotGexExposureRepository extends JpaRepository<SpotGexExposure, SpotGexExposureId>, SpotGexExposureRepositoryCustom {

    @Query("""
        SELECT MAX(s.collectedAt)
        FROM SpotGexExposure s
        WHERE s.ticker = :ticker
        """)
    Optional<Instant> findLatestCollectedAtByTicker(@Param("ticker") String ticker);

    List<SpotGexExposure> findByTickerAndCollectedAtOrderByExpiryAscStrikeAsc(String ticker, Instant collectedAt);

    List<SpotGexExposure> findByTickerAndExpiryAndCollectedAtOrderByStrikeAsc(
        String ticker,
        LocalDate expiry,
        Instant collectedAt
    );

    List<SpotGexStatusProjection> findCollectionStatus(List<String> tickers);

    interface SpotGexHistoryProjection {
        Instant getSnapshotAt();
        String getTicker();
        LocalDate getExpiry();
        BigDecimal getStrike();
        BigDecimal getPrice();
        LocalDate getMarketDate();
        Instant getSourceTime();
        BigDecimal getCallDeltaOi();
        BigDecimal getCallGammaOi();
        BigDecimal getCallVannaOi();
        BigDecimal getCallCharmOi();
        BigDecimal getPutDeltaOi();
        BigDecimal getPutGammaOi();
        BigDecimal getPutVannaOi();
        BigDecimal getPutCharmOi();
        BigDecimal getCallDeltaVol();
        BigDecimal getCallGammaVol();
        BigDecimal getCallVannaVol();
        BigDecimal getCallCharmVol();
        BigDecimal getPutDeltaVol();
        BigDecimal getPutGammaVol();
        BigDecimal getPutVannaVol();
        BigDecimal getPutCharmVol();
        BigDecimal getCallDeltaBid();
        BigDecimal getCallDeltaAsk();
        BigDecimal getCallGammaBid();
        BigDecimal getCallGammaAsk();
        BigDecimal getCallVannaBid();
        BigDecimal getCallVannaAsk();
        BigDecimal getCallCharmBid();
        BigDecimal getCallCharmAsk();
        BigDecimal getPutDeltaBid();
        BigDecimal getPutDeltaAsk();
        BigDecimal getPutGammaBid();
        BigDecimal getPutGammaAsk();
        BigDecimal getPutVannaBid();
        BigDecimal getPutVannaAsk();
        BigDecimal getPutCharmBid();
        BigDecimal getPutCharmAsk();
    }

    interface SpotGexStatusProjection {
        String getTicker();
        Instant getLastCollectedAt();
        Long getRowsToday();
        Long getCollectionCycles();
    }
}
