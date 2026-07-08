package com.marketmono.service;

import com.marketmono.dto.WhaleTradeDto;
import com.marketmono.dto.WhaleTradeFilters;
import com.marketmono.dto.WhaleTradePagination;
import com.marketmono.dto.WhaleTradePageResponse;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.FlowAlertSpecifications.FlowAlertFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for whale flow operations
 * Handles large options trades (premium >= $10k)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhaleFlowService {
    
    public static final BigDecimal WHALE_MINIMUM_PREMIUM = new BigDecimal("10000.00");
    
    private final FlowAlertService flowAlertService;
    private final WhaleFlowMapperService mapperService;
    
    /**
     * Get whale trades data with pagination and filtering
     */
    public WhaleTradePageResponse getWhaleTradesData(
        int page,
        int pageSize,
        BigDecimal minPremium,
        BigDecimal maxPremium,
        Integer minSize,
        Integer maxSize,
        String session,
        String ticker,
        String sortBy
    ) {
        log.debug("Fetching whale trades: page={}, pageSize={}, minPremium={}, ticker={}, sortBy={}", 
            page, pageSize, minPremium, ticker, sortBy);
        
        // Apply default minimum premium if not specified
        BigDecimal effectiveMinPremium = (minPremium != null && minPremium.compareTo(WHALE_MINIMUM_PREMIUM) >= 0) 
            ? minPremium 
            : WHALE_MINIMUM_PREMIUM;
        
        // Build ticker list
        List<String> tickerList = (ticker != null && !ticker.isBlank()) 
            ? List.of(ticker.toUpperCase()) 
            : null;
        
        // Build sort based on sortBy parameter
        Sort sort = buildSort(sortBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        
        // Fetch flow alerts with whale premium filter
        var filter = new FlowAlertFilter(
            tickerList != null ? tickerList : List.of(),  // tickers - pass empty list instead of null
            null,                     // ignoreTickers
            null,                     // minExpiryDate
            null,                     // maxExpiryDate
            null,                     // expirationDates
            effectiveMinPremium,      // minPremium (whale threshold)
            maxPremium,               // maxPremium
            minSize,                  // minSize
            maxSize,                  // maxSize
            null,                     // minVolume
            null,                     // maxVolume
            null,                     // minMarketCap
            null,                     // maxMarketCap
            null,                     // optionType
            null,                     // calls
            null,                     // puts
            null,                     // sweepsOnly
            null,                     // floorsOnly
            null,                     // hasSweep
            null,                     // hasFloor
            null,                     // regular
            null,                     // bid
            null,                     // mid
            null,                     // ask
            null,                     // noSide
            null,                     // aboveAsk
            null,                     // belowBid
            null,                     // hasSingleleg
            null,                     // hasMultileg
            null,                     // allOpeningTrades
            null,                     // otmOnly
            null,                     // itmOnly
            null,                     // excludeDeepItm
            null,                     // sizeGreaterOi
            null                      // volumeGreaterOi
        );
        
        Page<FlowAlert> alertPage = flowAlertService.getFlowAlertsWithFilters(filter, pageable);
        
        // Map to DTOs
        List<WhaleTradeDto> whaleTrades = mapperService.toDtoList(alertPage.getContent());
        
        // Filter by market session if specified (post-processing)
        if (session != null && !session.isBlank()) {
            List<String> sessionFilters = List.of(session.split(","));
            whaleTrades = whaleTrades.stream()
                .filter(trade -> sessionFilters.contains(trade.market_session()))
                .toList();
        }
        
        // Build pagination info
        WhaleTradePagination pagination = new WhaleTradePagination(
            page,
            pageSize,
            alertPage.getTotalElements(),
            alertPage.getTotalPages(),
            alertPage.hasNext(),
            alertPage.hasPrevious()
        );
        
        // Build filters info
        WhaleTradeFilters filters = new WhaleTradeFilters(
            effectiveMinPremium,
            maxPremium,
            session,
            ticker
        );
        
        return new WhaleTradePageResponse(whaleTrades, pagination, filters);
    }
    
    /**
     * Build sort order based on sortBy parameter
     */
    private Sort buildSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "timestamp";
        }
        
        return switch (sortBy.toLowerCase()) {
            case "premium" -> Sort.by(Sort.Direction.DESC, "totalPremium", "tradeStartTime");
            case "size" -> Sort.by(Sort.Direction.DESC, "totalSize", "totalPremium", "tradeStartTime");
            case "price" -> Sort.by(Sort.Direction.DESC, "price", "totalPremium", "tradeStartTime");
            default -> Sort.by(Sort.Direction.DESC, "tradeStartTime", "totalPremium");
        };
    }
    
    /**
     * Get whale trades statistics
     */
    public WhaleTradePageResponse getTopWhaleTrades(int limit) {
        log.debug("Fetching top {} whale trades", limit);
        
        // Fetch with premium sorting
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "totalPremium"));
        
        var filter = new FlowAlertFilter(
            null,                     // tickers
            null,                     // ignoreTickers
            null,                     // minExpiryDate
            null,                     // maxExpiryDate
            null,                     // expirationDates
            WHALE_MINIMUM_PREMIUM,    // minPremium
            null,                     // maxPremium
            null,                     // minSize
            null,                     // maxSize
            null,                     // minVolume
            null,                     // maxVolume
            null,                     // minMarketCap
            null,                     // maxMarketCap
            null,                     // optionType
            null,                     // calls
            null,                     // puts
            null,                     // sweepsOnly
            null,                     // floorsOnly
            null,                     // hasSweep
            null,                     // hasFloor
            null,                     // regular
            null,                     // bid
            null,                     // mid
            null,                     // ask
            null,                     // noSide
            null,                     // aboveAsk
            null,                     // belowBid
            null,                     // hasSingleleg
            null,                     // hasMultileg
            null,                     // allOpeningTrades
            null,                     // otmOnly
            null,                     // itmOnly
            null,                     // excludeDeepItm
            null,                     // sizeGreaterOi
            null                      // volumeGreaterOi
        );
        
        Page<FlowAlert> alertPage = flowAlertService.getFlowAlertsWithFilters(filter, pageable);
        
        List<WhaleTradeDto> whaleTrades = mapperService.toDtoList(alertPage.getContent());
        
        WhaleTradePagination pagination = new WhaleTradePagination(
            1,
            limit,
            alertPage.getTotalElements(),
            alertPage.getTotalPages(),
            false,
            false
        );
        
        WhaleTradeFilters filters = new WhaleTradeFilters(
            WHALE_MINIMUM_PREMIUM,
            null,
            null,
            null
        );
        
        return new WhaleTradePageResponse(whaleTrades, pagination, filters);
    }
}
