package com.marketmono.repository;

import com.marketmono.entity.TickerOptionsVolume;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TickerOptionsVolumeRepository extends JpaRepository<TickerOptionsVolume, Long> {

    List<TickerOptionsVolume> findByTickerOrderByDateDesc(String ticker, Pageable pageable);

    boolean existsByTicker(String ticker);

    /**
     * Upsert a daily snapshot keyed by (ticker, date). The current trading
     * day's row is refreshed on every sync cycle as intraday numbers evolve.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO ticker_options_volume (
                ticker, date, call_volume, put_volume, call_premium, put_premium,
                call_open_interest, put_open_interest,
                call_volume_ask_side, call_volume_bid_side,
                put_volume_ask_side, put_volume_bid_side,
                bullish_premium, bearish_premium, net_call_premium, net_put_premium,
                avg_3_day_call_volume, avg_3_day_put_volume,
                avg_7_day_call_volume, avg_7_day_put_volume,
                avg_30_day_call_volume, avg_30_day_put_volume,
                created_at, updated_at
            ) VALUES (
                :ticker, :date, :callVolume, :putVolume, :callPremium, :putPremium,
                :callOpenInterest, :putOpenInterest,
                :callVolumeAskSide, :callVolumeBidSide,
                :putVolumeAskSide, :putVolumeBidSide,
                :bullishPremium, :bearishPremium, :netCallPremium, :netPutPremium,
                :avg3DayCallVolume, :avg3DayPutVolume,
                :avg7DayCallVolume, :avg7DayPutVolume,
                :avg30DayCallVolume, :avg30DayPutVolume,
                NOW(), NOW()
            )
            ON CONFLICT (ticker, date) DO UPDATE SET
                call_volume            = EXCLUDED.call_volume,
                put_volume             = EXCLUDED.put_volume,
                call_premium           = EXCLUDED.call_premium,
                put_premium            = EXCLUDED.put_premium,
                call_open_interest     = EXCLUDED.call_open_interest,
                put_open_interest      = EXCLUDED.put_open_interest,
                call_volume_ask_side   = EXCLUDED.call_volume_ask_side,
                call_volume_bid_side   = EXCLUDED.call_volume_bid_side,
                put_volume_ask_side    = EXCLUDED.put_volume_ask_side,
                put_volume_bid_side    = EXCLUDED.put_volume_bid_side,
                bullish_premium        = EXCLUDED.bullish_premium,
                bearish_premium        = EXCLUDED.bearish_premium,
                net_call_premium       = EXCLUDED.net_call_premium,
                net_put_premium        = EXCLUDED.net_put_premium,
                avg_3_day_call_volume  = EXCLUDED.avg_3_day_call_volume,
                avg_3_day_put_volume   = EXCLUDED.avg_3_day_put_volume,
                avg_7_day_call_volume  = EXCLUDED.avg_7_day_call_volume,
                avg_7_day_put_volume   = EXCLUDED.avg_7_day_put_volume,
                avg_30_day_call_volume = EXCLUDED.avg_30_day_call_volume,
                avg_30_day_put_volume  = EXCLUDED.avg_30_day_put_volume,
                updated_at             = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("ticker") String ticker,
        @Param("date") LocalDate date,
        @Param("callVolume") Long callVolume,
        @Param("putVolume") Long putVolume,
        @Param("callPremium") BigDecimal callPremium,
        @Param("putPremium") BigDecimal putPremium,
        @Param("callOpenInterest") Long callOpenInterest,
        @Param("putOpenInterest") Long putOpenInterest,
        @Param("callVolumeAskSide") Long callVolumeAskSide,
        @Param("callVolumeBidSide") Long callVolumeBidSide,
        @Param("putVolumeAskSide") Long putVolumeAskSide,
        @Param("putVolumeBidSide") Long putVolumeBidSide,
        @Param("bullishPremium") BigDecimal bullishPremium,
        @Param("bearishPremium") BigDecimal bearishPremium,
        @Param("netCallPremium") BigDecimal netCallPremium,
        @Param("netPutPremium") BigDecimal netPutPremium,
        @Param("avg3DayCallVolume") BigDecimal avg3DayCallVolume,
        @Param("avg3DayPutVolume") BigDecimal avg3DayPutVolume,
        @Param("avg7DayCallVolume") BigDecimal avg7DayCallVolume,
        @Param("avg7DayPutVolume") BigDecimal avg7DayPutVolume,
        @Param("avg30DayCallVolume") BigDecimal avg30DayCallVolume,
        @Param("avg30DayPutVolume") BigDecimal avg30DayPutVolume
    );
}
