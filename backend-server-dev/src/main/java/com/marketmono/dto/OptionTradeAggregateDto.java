package com.marketmono.dto;

import com.marketmono.entity.OptionTradeAggregate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OptionTradeAggregateDto(
    UUID aggregateId,
    Integer groupingVersion,
    String ticker,
    String optionChain,
    String side,
    String aggressor,
    String conditionSignature,
    Boolean isSweep,
    Boolean isFloorTrade,
    Boolean isMultileg,
    Instant firstExecutedAt,
    Instant lastExecutedAt,
    BigDecimal representativePrice,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Long totalSize,
    BigDecimal totalPremium,
    Integer tradeCount,
    Integer exchangeCount,
    LocalDate expiry,
    String optionType,
    BigDecimal strike,
    BigDecimal underlyingPriceFirst,
    BigDecimal underlyingPriceLast,
    Integer openInterest,
    Long volume
) {

    public static OptionTradeAggregateDto from(OptionTradeAggregate entity) {
        return new OptionTradeAggregateDto(
            entity.getAggregateId(),
            entity.getGroupingVersion(),
            entity.getTicker(),
            entity.getOptionChain(),
            entity.getSide(),
            entity.getAggressor(),
            entity.getConditionSignature(),
            entity.getIsSweep(),
            entity.getIsFloorTrade(),
            entity.getIsMultileg(),
            entity.getFirstExecutedAt(),
            entity.getLastExecutedAt(),
            entity.getRepresentativePrice(),
            entity.getMinPrice(),
            entity.getMaxPrice(),
            entity.getTotalSize(),
            entity.getTotalPremium(),
            entity.getTradeCount(),
            entity.getExchangeCount(),
            entity.getExpiry(),
            entity.getOptionType(),
            entity.getStrike(),
            entity.getUnderlyingPriceFirst(),
            entity.getUnderlyingPriceLast(),
            entity.getOpenInterest(),
            entity.getVolume()
        );
    }
}
