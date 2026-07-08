package com.marketmono.service;

import com.marketmono.dto.WhaleFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.entity.FlowAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for mapping FlowAlert entities to WebSocket WhaleFlowAlertDto
 * Provides complete data transformation for real-time frontend updates
 */
@Service
@RequiredArgsConstructor
public class WebSocketFlowAlertMapperService {
    
    private static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TRADE_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("MM/dd/yyyy, hh:mm:ss a");
    private static final String SESSION_PREMARKET = "Pre-Market";
    private static final String SESSION_INTRADAY = "Intraday";
    private static final String SESSION_AFTERHOURS = "After-Hours";
    
    /**
     * Map FlowAlert entity to WebSocket DTO with all required fields
     */
    public WhaleFlowAlertDto toWebSocketDto(FlowAlert alert) {
        if (alert == null) {
            return null;
        }
        
        String marketSession = detectMarketSession(alert.getTradeStartTime());
        String tradeDateFormatted = formatTradeDate(alert.getTradeStartTime());
        
        // Format expiry as ISO date
        String expiryDate = alert.getExpiryDate() != null 
            ? alert.getExpiryDate().toString()
            : "";
        
        // Format next earnings as ISO date
        String nextEarningsFormatted = alert.getNextEarningsDate() != null 
            ? alert.getNextEarningsDate().toString()
            : "";
        
        return new WhaleFlowAlertDto(
            alert.getAlertId().toString(),  // Convert UUID to String
            alert.getTicker(),
            alert.getOptionSymbol(),
            alert.getStrike(),
            expiryDate,
            alert.getOptionType() != null ? alert.getOptionType().name().toLowerCase() : "unknown",
            alert.getPrice(),
            alert.getBid(),
            alert.getAsk(),
            alert.getUnderlyingPrice(),
            alert.getTotalSize(),
            alert.getTotalPremium(),
            alert.getTradeCount(),
            alert.getVolume(),
            alert.getOpenInterest(),
            alert.getVolumeOiRatio(),
            alert.getImpliedVolatilityStart(),
            alert.getImpliedVolatilityEnd(),
            alert.getAlertRule() != null ? alert.getAlertRule().name() : null,
            alert.getHasSweep(),
            alert.getHasFloor(),
            alert.getHasSingleleg(),
            alert.getHasMultileg(),
            alert.getAllOpeningTrades(),
            alert.getSector() != null ? alert.getSector().toString() : null,
            alert.getMarketCap() != null ? new java.math.BigDecimal(alert.getMarketCap()) : null,
            nextEarningsFormatted,
            alert.getTradeStartTime(),
            alert.getTradeEndTime(),
            alert.getTotalAskSidePremium(),
            alert.getTotalBidSidePremium(),
            marketSession,
            tradeDateFormatted
        );
    }
    
    /**
     * Map UnusualWhalesFlowAlertDto directly to WebSocket DTO for immediate broadcasting
     * This bypasses database operations for real-time updates
     */
    public WhaleFlowAlertDto toWebSocketDtoFromRaw(UnusualWhalesFlowAlertDto dto) {
        if (dto == null) {
            return null;
        }
        
        // Convert startTime from milliseconds to Instant
        Instant tradeStartTime = dto.startTime() != null 
            ? Instant.ofEpochMilli(dto.startTime()) 
            : Instant.now();
        
        String marketSession = detectMarketSession(tradeStartTime);
        String tradeDateFormatted = formatTradeDate(tradeStartTime);
        
        // Format expiry as ISO date
        String expiryDate = dto.expiry() != null 
            ? dto.expiry().toString()
            : "";
        
        // Format next earnings as ISO date  
        String nextEarningsFormatted = dto.nextEarningsDate() != null 
            ? dto.nextEarningsDate().toString()
            : "";
        
        return new WhaleFlowAlertDto(
            dto.id(),
            dto.ticker(),
            dto.optionChain(),
            dto.strike(),
            expiryDate,
            dto.type() != null ? dto.type().toLowerCase() : "unknown",
            dto.price(),
            dto.bid(),
            dto.ask(),
            dto.underlyingPrice(),
            dto.totalSize(),
            dto.totalPremium(),
            dto.tradeCount(),
            dto.volume(),
            dto.openInterest(),
            dto.volumeOiRatio(),
            dto.ivStart(),
            dto.ivEnd(),
            dto.alertRule(),
            dto.hasSweep(),
            dto.hasFloor(),
            dto.hasSingleleg(),
            dto.hasMultileg(),
            dto.allOpeningTrades(),
            dto.sector(),
            dto.marketCap(),
            nextEarningsFormatted,
            tradeStartTime,
            dto.endTime() != null ? Instant.ofEpochMilli(dto.endTime()) : null,
            dto.totalAskSidePremium(),
            dto.totalBidSidePremium(),
            marketSession,
            tradeDateFormatted
        );
    }
    
    /**
     * Detect market session based on trade time (Eastern Time)
     * Pre-Market: 4:00 AM - 9:30 AM ET
     * Intraday: 9:30 AM - 4:00 PM ET
     * After-Hours: 4:00 PM - 8:00 PM ET
     */
    private String detectMarketSession(Instant tradeTime) {
        if (tradeTime == null) {
            return SESSION_INTRADAY;
        }
        
        ZonedDateTime et = tradeTime.atZone(EASTERN_TIME);
        int hour = et.getHour();
        int minute = et.getMinute();
        
        int timeInMinutes = hour * 60 + minute;
        
        if (timeInMinutes >= 240 && timeInMinutes < 570) {
            return SESSION_PREMARKET;
        } else if (timeInMinutes >= 570 && timeInMinutes < 960) {
            return SESSION_INTRADAY;
        } else if (timeInMinutes >= 960 && timeInMinutes < 1200) {
            return SESSION_AFTERHOURS;
        } else {
            return SESSION_INTRADAY;
        }
    }
    
    /**
     * Format trade date in Eastern Time: "MM/DD/YYYY, HH:MM:SS AM/PM"
     */
    private String formatTradeDate(Instant tradeTime) {
        if (tradeTime == null) {
            return "";
        }
        
        ZonedDateTime et = tradeTime.atZone(EASTERN_TIME);
        return et.format(TRADE_DATE_FORMATTER);
    }
}
