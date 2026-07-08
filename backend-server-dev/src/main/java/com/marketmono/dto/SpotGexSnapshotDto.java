package com.marketmono.dto;

import com.marketmono.entity.SpotGexExposure;
import com.marketmono.repository.SpotGexExposureRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Persisted spot GEX snapshot row used by REST and WebSocket responses.
 */
public record SpotGexSnapshotDto(
    String ticker,
    LocalDate expiry,
    BigDecimal strike,
    Instant snapshotAt,
    BigDecimal price,
    LocalDate marketDate,
    Instant sourceTime,
    BigDecimal callDeltaOi,
    BigDecimal callGammaOi,
    BigDecimal callVannaOi,
    BigDecimal callCharmOi,
    BigDecimal putDeltaOi,
    BigDecimal putGammaOi,
    BigDecimal putVannaOi,
    BigDecimal putCharmOi,
    BigDecimal callDeltaVol,
    BigDecimal callGammaVol,
    BigDecimal callVannaVol,
    BigDecimal callCharmVol,
    BigDecimal putDeltaVol,
    BigDecimal putGammaVol,
    BigDecimal putVannaVol,
    BigDecimal putCharmVol,
    BigDecimal callDeltaBid,
    BigDecimal callDeltaAsk,
    BigDecimal callGammaBid,
    BigDecimal callGammaAsk,
    BigDecimal callVannaBid,
    BigDecimal callVannaAsk,
    BigDecimal callCharmBid,
    BigDecimal callCharmAsk,
    BigDecimal putDeltaBid,
    BigDecimal putDeltaAsk,
    BigDecimal putGammaBid,
    BigDecimal putGammaAsk,
    BigDecimal putVannaBid,
    BigDecimal putVannaAsk,
    BigDecimal putCharmBid,
    BigDecimal putCharmAsk
) {
    public static SpotGexSnapshotDto from(SpotGexExposure entity) {
        return new SpotGexSnapshotDto(
            entity.getTicker(),
            entity.getExpiry(),
            entity.getStrike(),
            entity.getCollectedAt(),
            entity.getPrice(),
            entity.getMarketDate(),
            entity.getSourceTime(),
            entity.getCallDeltaOi(),
            entity.getCallGammaOi(),
            entity.getCallVannaOi(),
            entity.getCallCharmOi(),
            entity.getPutDeltaOi(),
            entity.getPutGammaOi(),
            entity.getPutVannaOi(),
            entity.getPutCharmOi(),
            entity.getCallDeltaVol(),
            entity.getCallGammaVol(),
            entity.getCallVannaVol(),
            entity.getCallCharmVol(),
            entity.getPutDeltaVol(),
            entity.getPutGammaVol(),
            entity.getPutVannaVol(),
            entity.getPutCharmVol(),
            entity.getCallDeltaBid(),
            entity.getCallDeltaAsk(),
            entity.getCallGammaBid(),
            entity.getCallGammaAsk(),
            entity.getCallVannaBid(),
            entity.getCallVannaAsk(),
            entity.getCallCharmBid(),
            entity.getCallCharmAsk(),
            entity.getPutDeltaBid(),
            entity.getPutDeltaAsk(),
            entity.getPutGammaBid(),
            entity.getPutGammaAsk(),
            entity.getPutVannaBid(),
            entity.getPutVannaAsk(),
            entity.getPutCharmBid(),
            entity.getPutCharmAsk()
        );
    }

    public static SpotGexSnapshotDto from(SpotGexExposureRepository.SpotGexHistoryProjection row) {
        return new SpotGexSnapshotDto(
            row.getTicker(),
            row.getExpiry(),
            row.getStrike(),
            row.getSnapshotAt(),
            row.getPrice(),
            row.getMarketDate(),
            row.getSourceTime(),
            row.getCallDeltaOi(),
            row.getCallGammaOi(),
            row.getCallVannaOi(),
            row.getCallCharmOi(),
            row.getPutDeltaOi(),
            row.getPutGammaOi(),
            row.getPutVannaOi(),
            row.getPutCharmOi(),
            row.getCallDeltaVol(),
            row.getCallGammaVol(),
            row.getCallVannaVol(),
            row.getCallCharmVol(),
            row.getPutDeltaVol(),
            row.getPutGammaVol(),
            row.getPutVannaVol(),
            row.getPutCharmVol(),
            row.getCallDeltaBid(),
            row.getCallDeltaAsk(),
            row.getCallGammaBid(),
            row.getCallGammaAsk(),
            row.getCallVannaBid(),
            row.getCallVannaAsk(),
            row.getCallCharmBid(),
            row.getCallCharmAsk(),
            row.getPutDeltaBid(),
            row.getPutDeltaAsk(),
            row.getPutGammaBid(),
            row.getPutGammaAsk(),
            row.getPutVannaBid(),
            row.getPutVannaAsk(),
            row.getPutCharmBid(),
            row.getPutCharmAsk()
        );
    }

    public static SpotGexSnapshotDto from(SpotExposureByStrikeDto row, String ticker, Instant snapshotAt) {
        return new SpotGexSnapshotDto(
            ticker,
            row.expiry(),
            row.strike(),
            snapshotAt,
            row.price(),
            row.date(),
            row.time() == null ? snapshotAt : row.time().toInstant(),
            row.callDeltaOi(),
            row.callGammaOi(),
            row.callVannaOi(),
            row.callCharmOi(),
            row.putDeltaOi(),
            row.putGammaOi(),
            row.putVannaOi(),
            row.putCharmOi(),
            row.callDeltaVol(),
            row.callGammaVol(),
            row.callVannaVol(),
            row.callCharmVol(),
            row.putDeltaVol(),
            row.putGammaVol(),
            row.putVannaVol(),
            row.putCharmVol(),
            row.callDeltaBid(),
            row.callDeltaAsk(),
            row.callGammaBid(),
            row.callGammaAsk(),
            row.callVannaBid(),
            row.callVannaAsk(),
            row.callCharmBid(),
            row.callCharmAsk(),
            row.putDeltaBid(),
            row.putDeltaAsk(),
            row.putGammaBid(),
            row.putGammaAsk(),
            row.putVannaBid(),
            row.putVannaAsk(),
            row.putCharmBid(),
            row.putCharmAsk()
        );
    }

    public SpotGexSnapshotDto withTicker(String replacementTicker) {
        return new SpotGexSnapshotDto(
            replacementTicker,
            expiry,
            strike,
            snapshotAt,
            price,
            marketDate,
            sourceTime,
            callDeltaOi,
            callGammaOi,
            callVannaOi,
            callCharmOi,
            putDeltaOi,
            putGammaOi,
            putVannaOi,
            putCharmOi,
            callDeltaVol,
            callGammaVol,
            callVannaVol,
            callCharmVol,
            putDeltaVol,
            putGammaVol,
            putVannaVol,
            putCharmVol,
            callDeltaBid,
            callDeltaAsk,
            callGammaBid,
            callGammaAsk,
            callVannaBid,
            callVannaAsk,
            callCharmBid,
            callCharmAsk,
            putDeltaBid,
            putDeltaAsk,
            putGammaBid,
            putGammaAsk,
            putVannaBid,
            putVannaAsk,
            putCharmBid,
            putCharmAsk
        );
    }

    public SpotGexSnapshotDto withDeltaFrom(SpotExposureByStrikeDto row) {
        return withMissingFieldsFrom(row);
    }

    public SpotGexSnapshotDto withMissingFieldsFrom(SpotExposureByStrikeDto row) {
        if (row == null) {
            return this;
        }

        return new SpotGexSnapshotDto(
            ticker,
            expiry,
            strike,
            snapshotAt,
            prefer(price, row.price()),
            prefer(marketDate, row.date()),
            sourceTime != null || row.time() == null ? sourceTime : row.time().toInstant(),
            prefer(callDeltaOi, row.callDeltaOi()),
            prefer(callGammaOi, row.callGammaOi()),
            prefer(callVannaOi, row.callVannaOi()),
            prefer(callCharmOi, row.callCharmOi()),
            prefer(putDeltaOi, row.putDeltaOi()),
            prefer(putGammaOi, row.putGammaOi()),
            prefer(putVannaOi, row.putVannaOi()),
            prefer(putCharmOi, row.putCharmOi()),
            prefer(callDeltaVol, row.callDeltaVol()),
            prefer(callGammaVol, row.callGammaVol()),
            prefer(callVannaVol, row.callVannaVol()),
            prefer(callCharmVol, row.callCharmVol()),
            prefer(putDeltaVol, row.putDeltaVol()),
            prefer(putGammaVol, row.putGammaVol()),
            prefer(putVannaVol, row.putVannaVol()),
            prefer(putCharmVol, row.putCharmVol()),
            prefer(callDeltaBid, row.callDeltaBid()),
            prefer(callDeltaAsk, row.callDeltaAsk()),
            prefer(callGammaBid, row.callGammaBid()),
            prefer(callGammaAsk, row.callGammaAsk()),
            prefer(callVannaBid, row.callVannaBid()),
            prefer(callVannaAsk, row.callVannaAsk()),
            prefer(callCharmBid, row.callCharmBid()),
            prefer(callCharmAsk, row.callCharmAsk()),
            prefer(putDeltaBid, row.putDeltaBid()),
            prefer(putDeltaAsk, row.putDeltaAsk()),
            prefer(putGammaBid, row.putGammaBid()),
            prefer(putGammaAsk, row.putGammaAsk()),
            prefer(putVannaBid, row.putVannaBid()),
            prefer(putVannaAsk, row.putVannaAsk()),
            prefer(putCharmBid, row.putCharmBid()),
            prefer(putCharmAsk, row.putCharmAsk())
        );
    }

    private static LocalDate prefer(LocalDate existing, LocalDate fallback) {
        return existing != null ? existing : fallback;
    }

    private static BigDecimal prefer(BigDecimal existing, BigDecimal fallback) {
        return existing != null ? existing : fallback;
    }
}
