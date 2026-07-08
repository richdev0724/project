package com.marketmono.service.kafka;

import com.marketmono.entity.OptionTrade;
import com.marketmono.proto.client.TransactionsProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps protobuf OptionTrade messages from Kafka to JPA entity.
 * Proto field numbers match the actual upstream market data provider feed schema (March 2026).
 * Includes validation to skip malformed records from the upstream feed.
 */
@Component
@Slf4j
public class OptionTradeProtoMapper {

    private static final Instant MIN_VALID_TIMESTAMP = Instant.parse("2020-01-01T00:00:00Z");

    /**
     * Maps a protobuf OptionTrade to a JPA entity.
     * Returns null if the record fails validation (caller must filter nulls).
     */
    public OptionTrade toEntity(TransactionsProto.OptionTrade proto) {
        log.trace("[KAFKA] Mapping option trade: id={}, ticker={}, size={}, premium={}",
            proto.getId(), proto.getUnderlyingSymbol(), proto.getSize(), proto.getPremium());

        if (!isValid(proto)) {
            return null;
        }

        // Extract side from tags (first tag is typically side: "ask_side", "bid_side", "mid")
        List<String> tags = proto.getTagsList();
        String side = tags.isEmpty() ? null : tags.get(0);

        // Build condition signature from upstream trade code + report flags.
        List<String> flags = proto.getReportFlagsList();
        String reportCondition = flags.isEmpty() ? null : String.join(",", flags);
        String upstreamConditionDetails = normalizeTradeCode(proto.getUpstreamCondDetails());
        String condition = buildConditionSignature(upstreamConditionDetails, reportCondition);

        // Determine boolean flags from upstream trade code and report flags.
        boolean isSweep = flags.stream().anyMatch(this::flagIndicatesSweep);
        boolean isMultileg = isMultiLegTradeCode(upstreamConditionDetails)
            || (upstreamConditionDetails == null && tags.stream().anyMatch(this::tagIndicatesMultileg));

        return OptionTrade.builder()
            .tradeId(UUID.fromString(proto.getId()))
            .ticker(proto.getUnderlyingSymbol())
            .price(parseBigDecimal(proto.getPrice()))
            .size(proto.getSize())
            .nbboBid(parseBigDecimalOrNull(proto.getNbboBid()))
            .nbboAsk(parseBigDecimalOrNull(proto.getNbboAsk()))
            .underlyingPrice(parseBigDecimal(proto.getUnderlyingPrice()))
            .exchange(proto.getExchange().isEmpty() ? null : proto.getExchange())
            .executedAt(Instant.ofEpochMilli(proto.getExecutedAt()))
            .optionChain(proto.getOptionChain())
            .condition(condition)
            .isMultileg(isMultileg)
            .isFloorTrade(false) // Not directly available in new schema
            .isSweep(isSweep)
            .aggressor(null) // Not in new schema
            .premium(parseBigDecimalOrNull(proto.getPremium()))
            .side(side)
            .expiry(parseLocalDateOrNull(proto.getExpiry()))
            .optionType(proto.getOptionType().isEmpty() ? null : proto.getOptionType())
            .openInterest(proto.getOpenInterest() == 0 ? null : proto.getOpenInterest())
            .strike(parseBigDecimalOrNull(proto.getStrike()))
            .volume(proto.getVolume() == 0 ? null : proto.getVolume())
            .ewmaNbboAsk(parseBigDecimalOrNull(proto.getEwmaNbboAsk()))
            .ewmaNbboBid(parseBigDecimalOrNull(proto.getEwmaNbboBid()))
            .impliedVolatility(parseBigDecimalOrNull(proto.getImpliedVolatility()))
            .delta(parseBigDecimalOrNull(proto.getDelta()))
            .theta(parseBigDecimalOrNull(proto.getTheta()))
            .gamma(parseBigDecimalOrNull(proto.getGamma()))
            .vega(parseBigDecimalOrNull(proto.getVega()))
            .rho(parseBigDecimalOrNull(proto.getRho()))
            .theo(parseBigDecimalOrNull(proto.getTheo()))
            .askVol(proto.getAskVol() == 0 ? null : proto.getAskVol())
            .bidVol(proto.getBidVol() == 0 ? null : proto.getBidVol())
            .noSideVol(proto.getNoSideVol() == 0 ? null : proto.getNoSideVol())
            .midVol(proto.getMidVol() == 0 ? null : proto.getMidVol())
            .multiVol(proto.getMultiVol() == 0 ? null : proto.getMultiVol())
            .stockMultiVol(proto.getStockMultiVol() == 0 ? null : proto.getStockMultiVol())
            .build();
    }

    /**
     * Validates a protobuf OptionTrade record. Rejects clearly malformed records
     * that would cause DB constraint violations or contain garbage data.
     */
    private boolean isValid(TransactionsProto.OptionTrade proto) {
        // executed_at = 0 means epoch 1970, clearly invalid for a live trade
        if (proto.getExecutedAt() <= 0) {
            log.warn("[KAFKA] Skipping malformed trade: id={}, ticker={} — executed_at is 0",
                proto.getId(), proto.getUnderlyingSymbol());
            return false;
        }

        Instant executedAt = Instant.ofEpochMilli(proto.getExecutedAt());
        if (executedAt.isBefore(MIN_VALID_TIMESTAMP)) {
            log.warn("[KAFKA] Skipping malformed trade: id={}, ticker={} — executed_at {} is before 2020",
                proto.getId(), proto.getUnderlyingSymbol(), executedAt);
            return false;
        }

        // option_chain is required
        if (proto.getOptionChain().isEmpty()) {
            log.warn("[KAFKA] Skipping malformed trade: id={}, ticker={} — option_chain is empty",
                proto.getId(), proto.getUnderlyingSymbol());
            return false;
        }

        // ticker must not be empty
        if (proto.getUnderlyingSymbol().isEmpty()) {
            log.warn("[KAFKA] Skipping malformed trade: id={} — underlying_symbol is empty", proto.getId());
            return false;
        }

        // id must be a valid UUID
        try {
            UUID.fromString(proto.getId());
        } catch (IllegalArgumentException e) {
            log.warn("[KAFKA] Skipping malformed trade: id={} — invalid UUID", proto.getId());
            return false;
        }

        return true;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("[KAFKA] Failed to parse BigDecimal: '{}'", value);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseBigDecimalOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("[KAFKA] Failed to parse BigDecimal: '{}'", value);
            return null;
        }
    }

    private LocalDate parseLocalDateOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            log.warn("[KAFKA] Failed to parse LocalDate: '{}'", value);
            return null;
        }
    }

    private String normalizeTradeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String buildConditionSignature(String tradeCode, String reportCondition) {
        if (tradeCode == null || tradeCode.isBlank()) {
            return reportCondition;
        }

        if (reportCondition == null || reportCondition.isBlank()) {
            return tradeCode;
        }

        return tradeCode + "|" + reportCondition;
    }

    private boolean isMultiLegTradeCode(String tradeCode) {
        if (tradeCode == null || tradeCode.isBlank()) {
            return false;
        }

        return switch (tradeCode.charAt(0)) {
            case 'M', 'T', 'C' -> true;
            default -> false;
        };
    }

    private boolean flagIndicatesSweep(String flag) {
        return flag != null && flag.toLowerCase(Locale.ROOT).contains("sweep");
    }

    private boolean tagIndicatesMultileg(String tag) {
        return tag != null && tag.toLowerCase(Locale.ROOT).contains("multi");
    }
}
