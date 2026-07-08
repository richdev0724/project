package com.marketmono.dto;

import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight DTO for broadcasting option trades via WebSocket and REST responses.
 * Excludes internal fields (createdAt, isNewEntity) to minimize payload size.
 */
public record OptionTradeDto(
    UUID tradeId,
    String ticker,
    String optionChain,
    BigDecimal price,
    Integer size,
    BigDecimal premium,
    BigDecimal underlyingPrice,
    BigDecimal nbboBid,
    BigDecimal nbboAsk,
    String exchange,
    String side,
    String condition,
    String aggressor,
    Boolean isSweep,
    Boolean isFloorTrade,
    Boolean isMultileg,
    Instant executedAt,
    LocalDate expiry,
    String optionType,
    Integer openInterest,
    BigDecimal strike,
    Long volume,
    BigDecimal ewmaNbboAsk,
    BigDecimal ewmaNbboBid,
    BigDecimal impliedVolatility,
    BigDecimal delta,
    BigDecimal theta,
    BigDecimal gamma,
    BigDecimal vega,
    BigDecimal rho,
    BigDecimal theo,
    Long askVol,
    Long bidVol,
    Long noSideVol,
    Long midVol,
    Long multiVol,
    Long stockMultiVol
) {

    public static OptionTradeDto from(OptionTrade entity) {
        return new OptionTradeDto(
            entity.getTradeId(),
            entity.getTicker(),
            entity.getOptionChain(),
            entity.getPrice(),
            entity.getSize(),
            entity.getPremium(),
            entity.getUnderlyingPrice(),
            entity.getNbboBid(),
            entity.getNbboAsk(),
            entity.getExchange(),
            entity.getSide(),
            entity.getCondition(),
            entity.getAggressor(),
            entity.getIsSweep(),
            entity.getIsFloorTrade(),
            entity.getIsMultileg(),
            entity.getExecutedAt(),
            entity.getExpiry(),
            entity.getOptionType(),
            entity.getOpenInterest(),
            entity.getStrike(),
            entity.getVolume(),
            entity.getEwmaNbboAsk(),
            entity.getEwmaNbboBid(),
            entity.getImpliedVolatility(),
            entity.getDelta(),
            entity.getTheta(),
            entity.getGamma(),
            entity.getVega(),
            entity.getRho(),
            entity.getTheo(),
            entity.getAskVol(),
            entity.getBidVol(),
            entity.getNoSideVol(),
            entity.getMidVol(),
            entity.getMultiVol(),
            entity.getStockMultiVol()
        );
    }

    /**
     * Create OptionTradeDto from a FlowAlert entity, mapping available fields.
     * Fields not present on FlowAlert are set to null or derived equivalents.
     */
    public static OptionTradeDto from(FlowAlert alert) {
        // Derive side from ask/bid premium comparison
        String side = null;
        if (alert.getTotalAskSidePremium() != null && alert.getTotalBidSidePremium() != null) {
            side = alert.getTotalAskSidePremium().compareTo(alert.getTotalBidSidePremium()) >= 0
                ? "ASK" : "BID";
        }

        return new OptionTradeDto(
            alert.getAlertId(),
            alert.getTicker(),
            alert.getOptionSymbol(),
            alert.getPrice(),
            alert.getTotalSize(),
            alert.getTotalPremium(),
            alert.getUnderlyingPrice(),
            alert.getBid(),
            alert.getAsk(),
            null,   // exchange
            side,
            null,   // condition
            null,   // aggressor
            alert.getHasSweep(),
            alert.getHasFloor(),
            alert.getHasMultileg(),
            alert.getTradeStartTime(),
            null,   // expiry
            null,   // optionType
            null,   // openInterest
            null,   // strike
            null,   // volume
            null,   // ewmaNbboAsk
            null,   // ewmaNbboBid
            null,   // impliedVolatility
            null,   // delta
            null,   // theta
            null,   // gamma
            null,   // vega
            null,   // rho
            null,   // theo
            null,   // askVol
            null,   // bidVol
            null,   // noSideVol
            null,   // midVol
            null,   // multiVol
            null    // stockMultiVol
        );
    }

    /**
     * Create OptionTradeDto from a live / recent upstream flow alert DTO.
     * Used as a fallback for alert bookmarks when the local flow_alerts table
     * has not persisted the alert yet.
     */
    public static OptionTradeDto from(UnusualWhalesFlowAlertDto alert) {
        String side = null;
        if (alert.totalAskSidePremium() != null && alert.totalBidSidePremium() != null) {
            side = alert.totalAskSidePremium().compareTo(alert.totalBidSidePremium()) >= 0
                ? "ASK" : "BID";
        }

        return new OptionTradeDto(
            UUID.fromString(alert.id()),
            alert.ticker(),
            alert.optionChain(),
            alert.price(),
            alert.totalSize(),
            alert.totalPremium(),
            alert.underlyingPrice(),
            alert.bid(),
            alert.ask(),
            null,
            side,
            null,
            null,
            alert.hasSweep(),
            alert.hasFloor(),
            alert.hasMultileg(),
            alert.startTime() != null ? Instant.ofEpochMilli(alert.startTime()) : null,
            alert.expiry(),
            alert.type(),
            alert.openInterest(),
            alert.strike(),
            alert.volume() != null ? alert.volume().longValue() : null,
            null,
            null,
            alert.ivEnd() != null ? alert.ivEnd() : alert.ivStart(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
