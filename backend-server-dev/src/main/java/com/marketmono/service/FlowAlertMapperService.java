package com.marketmono.service;

import com.marketmono.dto.FlowAlertResponseDto;
import com.marketmono.entity.FlowAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Mapper service to transform FlowAlert entity to frontend DTO format
 */
@Service
@RequiredArgsConstructor
public class FlowAlertMapperService {
    
    public FlowAlertResponseDto toDto(FlowAlert alert, int index) {
        var source = createFlowSource(alert);
        
        return new FlowAlertResponseDto(
            String.valueOf(index),
            alert.getAlertId().toString(),  // Convert UUID to String
            null,
            source,
            null
        );
    }
    
    public List<FlowAlertResponseDto> toDtoList(List<FlowAlert> alerts) {
        return alerts.stream()
            .map(alert -> toDto(alert, alerts.indexOf(alert) + 1))
            .toList();
    }
    
    private FlowAlertResponseDto.FlowSource createFlowSource(FlowAlert alert) {
        // Calculate DTE
        int dte = (int) ChronoUnit.DAYS.between(LocalDateTime.now(), alert.getExpiryDate().atStartOfDay());
        
        // Format timestamps
        long timestamp = alert.getTradeStartTime().toEpochMilli();
        String timestampFriendly = alert.getTradeStartTime().toString();
        
        // Calculate mid price
        BigDecimal midPrice = BigDecimal.ZERO;
        if (alert.getBid() != null && alert.getAsk() != null) {
            midPrice = alert.getBid().add(alert.getAsk()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        }
        
        // Format contract ID: "strike type date"
        String contractId = String.format("%s %s %s",
            alert.getStrike().toString(),
            alert.getOptionType().name().toLowerCase(),
            alert.getExpiryDate().toString()
        );
        
        LocalDateTime tradeTime = LocalDateTime.ofInstant(alert.getTradeStartTime(), ZoneOffset.UTC);
        
        return new FlowAlertResponseDto.FlowSource(
            alert.getAlertId().toString(),  // Convert UUID to String
            alert.getOptionSymbol(),
            alert.getTicker(),
            contractId,
            alert.getPrice() != null ? alert.getPrice().toString() : "0",
            alert.getTotalSize() != null ? alert.getTotalSize() : 0,
            "0",
            alert.getBid() != null ? alert.getBid().toString() : "0",
            0,
            alert.getAsk() != null ? alert.getAsk().toString() : "0",
            0,
            midPrice.toString(),
            timestamp,
            timestampFriendly,
            timestamp,
            timestampFriendly,
            timestampFriendly,
            timestampFriendly,
            alert.getCreatedAt().toString(),
            alert.getUpdatedAt().toString(),
            alert.getTotalPremium() != null ? alert.getTotalPremium().toString() : "0",
            alert.getOptionType().name().toLowerCase(),
            alert.getStrike().toString(),
            alert.getExpiryDate().toString(),
            dte,
            alert.getUnderlyingPrice() != null ? alert.getUnderlyingPrice().toString() : "0",
            alert.getUnderlyingPrice(),
            alert.getExpiryDate().toString(),
            tradeTime.getDayOfMonth(),
            tradeTime.getHour(),
            tradeTime.getMinute(),
            tradeTime.getSecond(),
            tradeTime.getMonthValue(),
            "",
            null,
            0,
            0,
            null,
            0,
            0,
            null,
            null,
            null,
            null,
            alert.getOpenInterest() != null ? alert.getOpenInterest() : 0,
            null,
            null,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            alert.getHasSweep() != null && alert.getHasSweep() ? 1 : 0,
            0,
            0,
            alert.getHasFloor() != null && alert.getHasFloor() ? 1 : 0,
            0,
            alert.getHasSingleleg() != null && alert.getHasSingleleg() ? 1 : 0,
            alert.getHasMultileg() != null && alert.getHasMultileg() ? 1 : 0,
            0,
            0,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            null,
            alert.getVolume() != null ? alert.getVolume() : 0,
            null,
            null,
            0,
            null,
            null,
            "",
            "",
            "",
            null,
            null,
            null,
            0,
            0,
            0,
            0,
            null,
            alert.getAllOpeningTrades() != null && alert.getAllOpeningTrades() ? 1 : 0,
            alert.getSector() != null ? alert.getSector().getSectorName() : null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null
        );
    }
}
