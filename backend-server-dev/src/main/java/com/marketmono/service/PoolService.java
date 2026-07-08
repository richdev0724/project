package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.*;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Unified service for fetching and aggregating pool price level data
 * Handles both dark pool (off-lit) and lit pool (exchange) data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoolService {

    private final UnusualWhalesClient uwClient;

    /**
     * Get pool price levels for a ticker across multiple days
     *
     * @param ticker Stock ticker symbol
     * @param days Number of days to look back (default: 1)
     * @param limit Maximum number of levels to return
     * @param poolType Type of pool data to fetch
     * @param volumeExtractor Function to extract volume from OffLitPriceLevelDto
     * @return PoolLevelsResponse with aggregated pool levels
     */
    public PoolLevelsResponse getPoolLevels(String ticker, int days, int limit,
                                           PoolLevel.PoolType poolType,
                                           ToLongFunction<OffLitPriceLevelDto> volumeExtractor) {
        log.debug("Fetching {} levels for {}: days={}, limit={}", poolType, ticker, days, limit);

        // 1. Fetch volume-price data for date range
        List<OffLitPriceLevelDto> allLevels = new ArrayList<>();
        LocalDate endDate = DateUtils.getCurrentDateInEasternTime();

        for (int i = 0; i < days; i++) {
            LocalDate date = endDate.minusDays(i);
            try {
                StockVolumePriceLevelsResponse response = uwClient.getStockVolumePriceLevels(
                    ticker,
                    date.toString()
                );

                if (response != null && response.data() != null) {
                    log.debug("Fetched {} price levels for {} on {}", response.data().size(), ticker, date);
                    allLevels.addAll(response.data());
                }
            } catch (Exception e) {
                log.warn("Failed to fetch {} levels for {} on {}: {}", poolType, ticker, date, e.getMessage());
                // Continue with other dates even if one fails
            }
        }

        log.debug("Total price levels fetched: {}", allLevels.size());

        // 2. Transform to frontend format
        List<PoolLevel> levels = allLevels.stream()
            .filter(level -> volumeExtractor.applyAsLong(level) > 0) // Only include levels with volume
            .map(level -> {
                double price = Double.parseDouble(level.price());
                long size = volumeExtractor.applyAsLong(level);
                double totalPremium = size * price;

                return switch (poolType) {
                    case DARK_POOL -> PoolLevel.darkPool(ticker, price, size, totalPremium, 1, "regular", endDate);
                    case LIT_POOL -> PoolLevel.litPool(ticker, price, size, totalPremium, 1, "regular", endDate);
                };
            })
            .sorted(Comparator.comparingDouble(PoolLevel::totalPremium).reversed())
            .limit(limit)
            .toList();

        log.debug("Returning {} {} levels for {}", levels.size(), poolType, ticker);

        return switch (poolType) {
            case DARK_POOL -> PoolLevelsResponse.darkPool(ticker, levels);
            case LIT_POOL -> PoolLevelsResponse.litPool(ticker, levels);
        };
    }

    /**
     * Get dark pool price levels for a ticker across multiple days
     *
     * @param ticker Stock ticker symbol
     * @param days Number of days to look back (default: 1)
     * @param limit Maximum number of levels to return
     * @return PoolLevelsResponse with aggregated dark pool levels
     */
    public PoolLevelsResponse getDarkPoolLevels(String ticker, int days, int limit) {
        return getPoolLevels(ticker, days, limit, PoolLevel.PoolType.DARK_POOL,
                           OffLitPriceLevelDto::offVol);
    }

    /**
     * Get lit pool (exchange) price levels for a ticker across multiple days
     *
     * @param ticker Stock ticker symbol
     * @param days Number of days to look back (default: 1)
     * @param limit Maximum number of levels to return
     * @return PoolLevelsResponse with aggregated lit pool levels
     */
    public PoolLevelsResponse getLitPoolLevels(String ticker, int days, int limit) {
        return getPoolLevels(ticker, days, limit, PoolLevel.PoolType.LIT_POOL,
                           OffLitPriceLevelDto::litVol);
    }
}
