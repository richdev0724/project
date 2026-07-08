package com.marketmono.service;

import com.marketmono.dto.WhaleTradeDto;
import com.marketmono.entity.FlowAlert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for mapping FlowAlert entities to WhaleTradeDto
 */
@Service
@RequiredArgsConstructor
public class WhaleFlowMapperService {
    
    private static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TRADE_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("MM/dd/yyyy, hh:mm:ss a");
    private static final String DEFAULT_EXCHANGE_ID = "MARKET_DATA";
    
    /**
     * Map a single FlowAlert entity to WhaleTradeDto
     */
    public WhaleTradeDto toDto(FlowAlert flowAlert) {
        if (flowAlert == null) {
            return null;
        }
        
        String id = flowAlert.getAlertId().toString();  // Convert UUID to String
        
        String price = flowAlert.getPrice() != null 
            ? flowAlert.getPrice().setScale(2, java.math.RoundingMode.HALF_UP).toString()
            : "0.00";
        
        String totalPremium = flowAlert.getTotalPremium() != null 
            ? flowAlert.getTotalPremium().setScale(2, java.math.RoundingMode.HALF_UP).toString()
            : "0.00";
        
        String marketSession = detectMarketSession(flowAlert.getTradeStartTime());
        
        String tradeDate = formatTradeDate(flowAlert.getTradeStartTime());
        
        String timestamp = flowAlert.getTradeStartTime() != null 
            ? flowAlert.getTradeStartTime().toString()
            : Instant.now().toString();
        
        // Extract exchange from option symbol or use default
        String exchangeId = extractExchangeId(flowAlert.getOptionSymbol());
        
        return new WhaleTradeDto(
            id,
            flowAlert.getTicker(),
            price,
            flowAlert.getTotalSize(),
            totalPremium,
            marketSession,
            tradeDate,
            timestamp,
            exchangeId
        );
    }
    
    /**
     * Map a list of FlowAlert entities to WhaleTradeDto list
     */
    public List<WhaleTradeDto> toDtoList(List<FlowAlert> flowAlerts) {
        return flowAlerts.stream()
            .map(this::toDto)
            .toList();
    }
    
    /**
     * Detect market session based on trade time
     * Pre-Market: 4:00 AM - 9:30 AM ET
     * Intraday: 9:30 AM - 4:00 PM ET
     * After-Hours: 4:00 PM - 8:00 PM ET
     */
    private String detectMarketSession(Instant tradeTime) {
        if (tradeTime == null) {
            return "Intraday";
        }
        
        ZonedDateTime et = tradeTime.atZone(EASTERN_TIME);
        int hour = et.getHour();
        int minute = et.getMinute();
        
        // Convert to minutes since midnight for easier comparison
        int timeInMinutes = hour * 60 + minute;
        
        // Pre-Market: 4:00 AM (240 min) - 9:30 AM (570 min)
        if (timeInMinutes >= 240 && timeInMinutes < 570) {
            return "Pre-Market";
        }
        // Intraday: 9:30 AM (570 min) - 4:00 PM (960 min)
        else if (timeInMinutes >= 570 && timeInMinutes < 960) {
            return "Intraday";
        }
        // After-Hours: 4:00 PM (960 min) - 8:00 PM (1200 min)
        else if (timeInMinutes >= 960 && timeInMinutes < 1200) {
            return "After-Hours";
        }
        // Outside all sessions, default to Intraday
        else {
            return "Intraday";
        }
    }
    
    /**
     * Format trade date to match frontend format: "MM/DD/YYYY, HH:MM:SS AM/PM"
     */
    private String formatTradeDate(Instant tradeTime) {
        if (tradeTime == null) {
            return "";
        }
        
        ZonedDateTime et = tradeTime.atZone(EASTERN_TIME);
        return et.format(TRADE_DATE_FORMATTER);
    }
    
    /**
     * Extract exchange ID from option symbol
     * Option symbols typically contain exchange info; use a neutral source fallback when absent.
     */
    private String extractExchangeId(String optionSymbol) {
        if (optionSymbol == null || optionSymbol.isBlank()) {
            return DEFAULT_EXCHANGE_ID;
        }
        
        // Try to extract exchange code from option symbol
        // Most option symbols follow format: TICKER + DATE + TYPE + STRIKE
        // Exchange info may not be present, so use a neutral fallback.
        return DEFAULT_EXCHANGE_ID;
    }
}
