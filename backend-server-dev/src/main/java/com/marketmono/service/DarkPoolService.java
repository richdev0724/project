package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DarkPoolRecentResponseDto;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.dto.DarkPoolTopTradesResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for fetching dark pool data from the upstream market data API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DarkPoolService {

    private static final int DARK_FLOW_SOURCE_LIMIT = 200;
    private static final int MAX_TOP_DARK_POOL_LIMIT = 200;
    private static final long LARGE_PREMIUM_MIN = 50_000_000L;
    private static final long HIGH_PREMIUM_MIN = 100_000_000L;
    
    private final UnusualWhalesClient unusualWhalesClient;
    
    /**
     * Fetch recent dark pool trades with optional filters
     * 
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param minPremium Minimum premium filter (default: 50000000)
     * @param maxPremium Maximum premium filter
     * @param minSize Minimum size filter
     * @param maxSize Maximum size filter
     * @param minVolume Minimum volume filter
     * @param maxVolume Maximum volume filter
     * @return Recent dark pool trades response
     */
    public DarkPoolRecentResponseDto getRecentDarkPoolTrades(
            String date,
            Integer limit,
            Long minPremium,
            Long maxPremium,
            Integer minSize,
            Integer maxSize,
            Integer minVolume,
            Integer maxVolume) {
        log.debug("Fetching recent dark pool trades from upstream market data API");
        
        try {
            DarkPoolRecentResponseDto response = unusualWhalesClient.getDarkPoolRecent(
                date, limit, minPremium, maxPremium, minSize, maxSize, minVolume, maxVolume
            );
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} recent dark pool trades", response.data().size());
            } else {
                log.warn("No recent dark pool trades returned from API");
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error fetching recent dark pool trades", e);
            throw new MarketDataProviderException("Failed to fetch recent dark pool trades: " + e.getMessage(), e);
        }
    }

    /**
     * Build the Market Dashboard Top Dark Pool list from the same recent-trades
     * source used by the Dark Flow page.
     */
    public DarkPoolTopTradesResponseDto getTopDarkPoolTradesForDay(LocalDate date, int limit) {
        String dateParam = date != null ? date.toString() : null;
        int cappedLimit = Math.min(Math.max(limit, 1), MAX_TOP_DARK_POOL_LIMIT);

        List<DarkPoolRecentTradeDto> trades = mergeByTrackingId(List.of(
            fetchRecentTradesData(dateParam, HIGH_PREMIUM_MIN, null),
            fetchRecentTradesData(dateParam, LARGE_PREMIUM_MIN, HIGH_PREMIUM_MIN)
        ));

        Map<DailyDarkPoolTradeKey, DailyDarkPoolTradeAggregate> aggregates = new HashMap<>();
        for (DarkPoolRecentTradeDto trade : trades) {
            if (!isAggregatableDarkFlowTrade(trade)) {
                continue;
            }

            BigDecimal displayPrice = trade.price().setScale(2, RoundingMode.HALF_UP);
            String tradeDate = trade.executedAt().substring(0, 10);
            DailyDarkPoolTradeKey key = new DailyDarkPoolTradeKey(
                trade.ticker().toUpperCase(Locale.ROOT),
                displayPrice,
                tradeDate
            );

            aggregates
                .computeIfAbsent(key, ignored -> new DailyDarkPoolTradeAggregate(trade, displayPrice))
                .add(trade);
        }

        List<DailyDarkPoolTradeAggregate> topAggregates = aggregates.values().stream()
            .sorted(Comparator
                .comparing(DailyDarkPoolTradeAggregate::premium, Comparator.reverseOrder())
                .thenComparing(DailyDarkPoolTradeAggregate::latestExecutedAt, Comparator.reverseOrder()))
            .limit(cappedLimit)
            .toList();

        List<DarkPoolRecentTradeDto> results = topAggregates.stream()
            .map(DailyDarkPoolTradeAggregate::toDto)
            .toList();

        Map<String, List<DarkPoolRecentTradeDto>> constituentsByTrackingId = topAggregates.stream()
            .collect(Collectors.toMap(
                aggregate -> String.valueOf(aggregate.latestTrade.trackingId()),
                DailyDarkPoolTradeAggregate::constituents,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        return new DarkPoolTopTradesResponseDto(results, constituentsByTrackingId);
    }
    
    /**
     * All dark-flow-visible trades for one market day: the same merged
     * 50M-100M and 100M+ premium bands the Dark Flow page displays.
     *
     * @param date Trading date in YYYY-MM-DD format (null = current market day)
     */
    public List<DarkPoolRecentTradeDto> getDarkFlowTradesForDay(String date) {
        return mergeByTrackingId(List.of(
            fetchRecentTradesData(date, HIGH_PREMIUM_MIN, null),
            fetchRecentTradesData(date, LARGE_PREMIUM_MIN, HIGH_PREMIUM_MIN)
        ));
    }

    /**
     * Fetch dark pool trades for a specific ticker
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param maxPremium Maximum premium filter
     * @param minPremium Minimum premium filter
     * @param newerThan Timestamp filter - return trades newer than this timestamp
     * @return Dark pool trades response for the ticker
     */
    public DarkPoolRecentResponseDto getDarkPoolTradesByTicker(
            String ticker,
            String date,
            Integer limit,
            Long maxPremium,
            Long minPremium,
            String newerThan) {
        log.debug("Fetching dark pool trades for ticker {} from upstream market data API", ticker);
        
        try {
            DarkPoolRecentResponseDto response = unusualWhalesClient.getDarkPoolByTicker(
                ticker, date, limit, maxPremium, minPremium, newerThan
            );
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} dark pool trades for ticker {}", response.data().size(), ticker);
            } else {
                log.warn("No dark pool trades returned from API for ticker {}", ticker);
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error fetching dark pool trades for ticker {}", ticker, e);
            throw new MarketDataProviderException("Failed to fetch dark pool trades for ticker " + ticker + ": " + e.getMessage(), e);
        }
    }

    private List<DarkPoolRecentTradeDto> fetchRecentTradesData(
        String date,
        Long minPremium,
        Long maxPremium
    ) {
        DarkPoolRecentResponseDto response = getRecentDarkPoolTrades(
            date,
            DARK_FLOW_SOURCE_LIMIT,
            minPremium,
            maxPremium,
            null,
            null,
            null,
            null
        );
        return response != null && response.data() != null ? response.data() : List.of();
    }

    private static List<DarkPoolRecentTradeDto> mergeByTrackingId(List<List<DarkPoolRecentTradeDto>> batches) {
        Map<Long, DarkPoolRecentTradeDto> seenByTrackingId = new LinkedHashMap<>();
        List<DarkPoolRecentTradeDto> merged = new ArrayList<>();

        for (List<DarkPoolRecentTradeDto> batch : batches) {
            for (DarkPoolRecentTradeDto trade : batch) {
                if (trade.trackingId() == null) {
                    merged.add(trade);
                    continue;
                }

                if (!seenByTrackingId.containsKey(trade.trackingId())) {
                    seenByTrackingId.put(trade.trackingId(), trade);
                    merged.add(trade);
                }
            }
        }

        return merged;
    }

    private static boolean isAggregatableDarkFlowTrade(DarkPoolRecentTradeDto trade) {
        return trade != null
            && trade.ticker() != null
            && !trade.ticker().isBlank()
            && trade.price() != null
            && trade.premium() != null
            && trade.executedAt() != null
            && trade.executedAt().length() >= 10
            && !Boolean.TRUE.equals(trade.canceled());
    }

    private static OffsetDateTime parseExecutedAt(String executedAt) {
        try {
            return OffsetDateTime.parse(executedAt);
        } catch (Exception ignored) {
            return OffsetDateTime.MIN;
        }
    }

    private record DailyDarkPoolTradeKey(String ticker, BigDecimal price, String tradeDate) {}

    private static final class DailyDarkPoolTradeAggregate {

        private DarkPoolRecentTradeDto latestTrade;
        private final BigDecimal displayPrice;
        private final List<DarkPoolRecentTradeDto> constituentTrades = new ArrayList<>();
        private BigDecimal premium = BigDecimal.ZERO;
        private int totalSize = 0;
        private int totalVolume = 0;

        private DailyDarkPoolTradeAggregate(DarkPoolRecentTradeDto firstTrade, BigDecimal displayPrice) {
            this.latestTrade = firstTrade;
            this.displayPrice = displayPrice;
        }

        private void add(DarkPoolRecentTradeDto trade) {
            constituentTrades.add(trade);
            premium = premium.add(trade.premium());
            totalSize += trade.size() != null ? trade.size() : 0;
            totalVolume += trade.volume() != null ? trade.volume() : 0;
            if (parseExecutedAt(trade.executedAt()).isAfter(parseExecutedAt(latestTrade.executedAt()))) {
                latestTrade = trade;
            }
        }

        private BigDecimal premium() {
            return premium.setScale(2, RoundingMode.HALF_UP);
        }

        private OffsetDateTime latestExecutedAt() {
            return parseExecutedAt(latestTrade.executedAt());
        }

        private DarkPoolRecentTradeDto toDto() {
            return new DarkPoolRecentTradeDto(
                latestTrade.ticker(),
                displayPrice,
                totalSize,
                totalVolume,
                latestTrade.executedAt(),
                premium(),
                latestTrade.nbboAsk(),
                latestTrade.nbboBid(),
                latestTrade.nbboAskQuantity(),
                latestTrade.nbboBidQuantity(),
                latestTrade.marketCenter(),
                latestTrade.canceled(),
                latestTrade.extHourSoldCodes(),
                latestTrade.saleCondCodes(),
                latestTrade.trackingId(),
                latestTrade.tradeCode(),
                latestTrade.tradeSettlement()
            );
        }

        private List<DarkPoolRecentTradeDto> constituents() {
            return constituentTrades;
        }
    }
}
