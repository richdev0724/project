package com.marketmono.service;

import com.marketmono.dto.*;
import com.marketmono.entity.*;
import com.marketmono.repository.DpLpAggregateRepository;
import com.marketmono.repository.DpLpTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DpLpService {

    private static final ZoneId MARKET_TIME_ZONE = ZoneId.of("America/New_York");
    private static final int MAX_TOP_DARK_POOL_LIMIT = 200;

    private final DpLpTradeRepository tradeRepository;
    private final DpLpAggregateRepository aggregateRepository;
    private final DarkPoolBroadcaster darkPoolBroadcaster;
    private final DarkPoolService darkPoolService;

    /**
     * Get DB-backed top dark-pool trades for one market day.
     *
     * <p>The stored off-lit feed does not persist the upstream premium field, so premium
     * is computed from stored price * size and grouped like the dark-flow page:
     * ticker + rounded display price + trade day.
     */
    @Transactional(readOnly = true)
    public DarkPoolTopTradesResponseDto getTopDarkPoolTradesForDay(LocalDate date, int limit) {
        LocalDate queryDate = date != null ? date : LocalDate.now(MARKET_TIME_ZONE);
        int cappedLimit = Math.min(Math.max(limit, 1), MAX_TOP_DARK_POOL_LIMIT);
        OffsetDateTime startTime = queryDate.atStartOfDay(MARKET_TIME_ZONE).toOffsetDateTime();
        OffsetDateTime endTime = queryDate.plusDays(1).atStartOfDay(MARKET_TIME_ZONE).toOffsetDateTime();

        List<DpLpTrade> trades = tradeRepository
            .findByVenueTypeAndTsBetweenOrderByTsAsc(VenueType.DARK, startTime, endTime);

        Map<DailyDarkPoolTradeKey, DailyDarkPoolTradeAggregate> aggregates = new HashMap<>();
        for (DpLpTrade trade : trades) {
            if (trade.getTicker() == null || trade.getPrice() == null || trade.getSize() == null) {
                continue;
            }

            BigDecimal price = trade.getPrice().setScale(2, RoundingMode.HALF_UP);
            DailyDarkPoolTradeKey key = new DailyDarkPoolTradeKey(
                trade.getTicker().toUpperCase(Locale.ROOT),
                price,
                trade.getTs().atZoneSameInstant(MARKET_TIME_ZONE).toLocalDate()
            );

            aggregates
                .computeIfAbsent(key, ignored -> new DailyDarkPoolTradeAggregate(trade, price))
                .add(trade);
        }

        List<DailyDarkPoolTradeAggregate> topAggregates = aggregates.values().stream()
            .sorted(Comparator
                .comparing(DailyDarkPoolTradeAggregate::premium, Comparator.reverseOrder())
                .thenComparing(DailyDarkPoolTradeAggregate::latestTimestamp, Comparator.reverseOrder()))
            .limit(cappedLimit)
            .toList();

        List<DarkPoolRecentTradeDto> results = topAggregates.stream()
            .map(DailyDarkPoolTradeAggregate::toDto)
            .toList();

        Map<String, List<DarkPoolRecentTradeDto>> constituentsByTrackingId = topAggregates.stream()
            .collect(Collectors.toMap(
                aggregate -> String.valueOf(aggregate.latestTrade.getTradeId()),
                DailyDarkPoolTradeAggregate::constituents
            ));

        return new DarkPoolTopTradesResponseDto(results, constituentsByTrackingId);
    }

    /**
     * Top dark-pool price levels by total premium.
     *
     * <p>Primary source is the stored {@code dp_lp_trades} table. When it has no
     * rows for the ticker (e.g. websocket ingestion disabled locally), falls back
     * to the same cached recent-trades source the Dark Flow page uses for the
     * current market day.
     *
     * @param ticker Stock ticker symbol
     * @param days Number of days to look back
     * @param limit Maximum number of levels to return
     */
    public List<DpLpTopLevelDto> getTopDarkPoolLevels(String ticker, int days, int limit) {
        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minus(days, ChronoUnit.DAYS);

        List<Object[]> rows = tradeRepository.findTopDarkPoolLevelsByPremium(
            ticker, startTime, endTime, limit
        );

        List<DpLpTopLevelDto> levels = rows.stream()
            .map(row -> new DpLpTopLevelDto(
                (BigDecimal) row[0],
                ((Number) row[1]).longValue(),
                row[2] instanceof BigDecimal premium
                    ? premium.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(((Number) row[2]).doubleValue()).setScale(2, RoundingMode.HALF_UP),
                ((Number) row[3]).longValue()
            ))
            .toList();

        if (!levels.isEmpty()) {
            return levels;
        }

        return getTopDarkPoolLevelsFromDarkFlowSource(ticker, limit);
    }

    /**
     * Fallback: aggregate the Dark Flow page's recent-trades feed (cached) into
     * top price levels for the requested ticker.
     */
    private List<DpLpTopLevelDto> getTopDarkPoolLevelsFromDarkFlowSource(String ticker, int limit) {
        List<DarkPoolRecentTradeDto> trades;
        try {
            trades = darkPoolService.getDarkFlowTradesForDay(null);
        } catch (Exception e) {
            log.warn("Dark-flow fallback for {} levels failed: {}", ticker, e.getMessage());
            return List.of();
        }

        Map<BigDecimal, long[]> byPrice = new HashMap<>();
        Map<BigDecimal, BigDecimal> premiumByPrice = new HashMap<>();

        for (DarkPoolRecentTradeDto trade : trades) {
            if (trade == null
                || trade.ticker() == null
                || !trade.ticker().equalsIgnoreCase(ticker)
                || trade.price() == null
                || Boolean.TRUE.equals(trade.canceled())) {
                continue;
            }

            BigDecimal price = trade.price().setScale(2, RoundingMode.HALF_UP);
            long size = trade.size() != null ? trade.size() : 0L;
            BigDecimal premium = trade.premium() != null
                ? trade.premium()
                : price.multiply(BigDecimal.valueOf(size));

            long[] sizeAndCount = byPrice.computeIfAbsent(price, ignored -> new long[2]);
            sizeAndCount[0] += size;
            sizeAndCount[1] += 1;
            premiumByPrice.merge(price, premium, BigDecimal::add);
        }

        return byPrice.entrySet().stream()
            .map(entry -> new DpLpTopLevelDto(
                entry.getKey(),
                entry.getValue()[0],
                premiumByPrice.get(entry.getKey()).setScale(2, RoundingMode.HALF_UP),
                entry.getValue()[1]
            ))
            .sorted(Comparator.comparing(DpLpTopLevelDto::totalPremium).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Get summary statistics for a ticker across dark pool and lit pool venues.
     */
    @Transactional(readOnly = true)
    public DpLpSummaryDto getSummary(String ticker, String window) {
        log.info("Fetching DP/LP summary for ticker: {}, window: {}", ticker, window);

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = calculateStartTime(endTime, window);
        BucketSize bucketSize = selectBucketSize(window);

        List<Object[]> summaries = aggregateRepository.getSummaryStatsByTicker(
            ticker, bucketSize, startTime, endTime
        );

        DpLpSummaryDto.VenueSummary darkSummary = null;
        DpLpSummaryDto.VenueSummary litSummary = null;

        for (Object[] row : summaries) {
            VenueType venueType = (VenueType) row[0];
            Integer totalTrades = ((Number) row[1]).intValue();
            Long totalVolume = ((Number) row[2]).longValue();
            BigDecimal avgVwap = row[3] != null 
                ? BigDecimal.valueOf(((Number) row[3]).doubleValue())
                : BigDecimal.ZERO;
            Long totalImbalance = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            DpLpSummaryDto.VenueSummary summary = new DpLpSummaryDto.VenueSummary(
                venueType, totalTrades, totalVolume, avgVwap, totalImbalance
            );

            if (venueType == VenueType.DARK) {
                darkSummary = summary;
            } else {
                litSummary = summary;
            }
        }

        // Default to zero values if no data found
        if (darkSummary == null) {
            darkSummary = new DpLpSummaryDto.VenueSummary(
                VenueType.DARK, 0, 0L, BigDecimal.ZERO, 0L
            );
        }
        if (litSummary == null) {
            litSummary = new DpLpSummaryDto.VenueSummary(
                VenueType.LIT, 0, 0L, BigDecimal.ZERO, 0L
            );
        }

        BigDecimal darkToLitRatio = calculateRatio(
            darkSummary.totalVolume(), litSummary.totalVolume()
        );

        List<DpLpSummaryDto.PriceLevel> topLevels = getTopPriceLevels(
            ticker, startTime, endTime, 10
        );

        return new DpLpSummaryDto(
            ticker, window, darkSummary, litSummary, darkToLitRatio, topLevels
        );
    }

    /**
     * Get time series data for a ticker and venue type.
     */
    @Transactional(readOnly = true)
    public DpLpSeriesDto getSeries(String ticker, VenueType venueType, BucketSize bucketSize, String window) {
        log.info("Fetching DP/LP series for ticker: {}, venue: {}, bucket: {}, window: {}",
            ticker, venueType, bucketSize, window);

        OffsetDateTime endTime = OffsetDateTime.now();
        OffsetDateTime startTime = calculateStartTime(endTime, window);

        List<DpLpAggregate> aggregates = aggregateRepository
            .findByTickerAndVenueTypeAndBucketSizeAndBucketStartBetweenOrderByBucketStartAsc(
                ticker, venueType, bucketSize, startTime, endTime
            );

        List<DpLpAggregateDto> seriesData = aggregates.stream()
            .map(this::toAggregateDto)
            .toList();

        return new DpLpSeriesDto(ticker, venueType, bucketSize, seriesData);
    }

    /**
     * Save trades and compute aggregates (called by collection service).
     */
    @Transactional
    public void saveTrades(List<DpLpTrade> trades) {
        if (trades.isEmpty()) {
            return;
        }

        log.info("Saving {} DP/LP trades", trades.size());
        tradeRepository.saveAll(trades);

        // Group trades by ticker, venue, and bucket for aggregation
        Map<AggregateKey, List<DpLpTrade>> groupedTrades = trades.stream()
            .collect(Collectors.groupingBy(trade -> new AggregateKey(
                trade.getTicker(),
                trade.getVenueType(),
                bucketStart(trade.getTs(), BucketSize.ONE_MINUTE)
            )));

        // Compute and save aggregates for each bucket
        for (BucketSize bucketSize : BucketSize.values()) {
            computeAndSaveAggregates(groupedTrades, bucketSize);
        }

        log.info("Completed aggregation for {} buckets", groupedTrades.size());
        
        // Broadcast dark pool and lit pool updates to frontend via WebSocket
        broadcastTradeUpdates(trades);
    }
    
    /**
     * Broadcast dark pool and lit pool updates to frontend clients via WebSocket.
     * Aggregates trades by ticker and venue type, then broadcasts top activity.
     * 
     * @param trades List of trades that were just saved
     */
    private void broadcastTradeUpdates(List<DpLpTrade> trades) {
        // Group trades by ticker and venue type
        Map<String, Map<VenueType, Long>> volumeByTickerAndVenue = trades.stream()
            .collect(Collectors.groupingBy(
                DpLpTrade::getTicker,
                Collectors.groupingBy(
                    DpLpTrade::getVenueType,
                    Collectors.summingLong(DpLpTrade::getSize)
                )
            ));
        
        // Broadcast dark pool and lit pool updates for each ticker
        volumeByTickerAndVenue.forEach((ticker, venueVolumes) -> {
            Long darkVolume = venueVolumes.getOrDefault(VenueType.DARK, 0L);
            Long litVolume = venueVolumes.getOrDefault(VenueType.LIT, 0L);
            
            if (darkVolume > 0) {
                darkPoolBroadcaster.broadcastDarkPoolUpdate(
                    new DarkPoolTopActivityDto(ticker, darkVolume)
                );
            }
            
            if (litVolume > 0) {
                darkPoolBroadcaster.broadcastLitPoolUpdate(
                    new LitPoolTopActivityDto(ticker, litVolume)
                );
            }
        });
        
        log.debug("Broadcasted DP/LP updates for {} tickers", volumeByTickerAndVenue.size());
    }

    /**
     * Compute aggregates for a specific bucket size.
     */
    private void computeAndSaveAggregates(Map<AggregateKey, List<DpLpTrade>> groupedTrades, BucketSize bucketSize) {
        for (Map.Entry<AggregateKey, List<DpLpTrade>> entry : groupedTrades.entrySet()) {
            AggregateKey key = entry.getKey();
            List<DpLpTrade> bucketTrades = entry.getValue();

            OffsetDateTime bucketStartTime = bucketStart(key.timestamp(), bucketSize);

            // Check if aggregate already exists
            Optional<DpLpAggregate> existing = aggregateRepository
                .findByTickerAndVenueTypeAndBucketStartAndBucketSize(
                    key.ticker(), key.venueType(), bucketStartTime, bucketSize
                );

            DpLpAggregate aggregate = existing.orElseGet(() -> DpLpAggregate.builder()
                .ticker(key.ticker())
                .venueType(key.venueType())
                .bucketStart(bucketStartTime)
                .bucketSize(bucketSize)
                .tradeCount(0)
                .totalVolume(0L)
                .netImbalance(0L)
                .build()
            );

            // Aggregate metrics
            int tradeCount = bucketTrades.size();
            long totalVolume = bucketTrades.stream().mapToLong(DpLpTrade::getSize).sum();
            BigDecimal vwap = calculateVwap(bucketTrades);
            BigDecimal minPrice = bucketTrades.stream()
                .map(DpLpTrade::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
            BigDecimal maxPrice = bucketTrades.stream()
                .map(DpLpTrade::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

            // Update aggregate
            aggregate.setTradeCount(aggregate.getTradeCount() + tradeCount);
            aggregate.setTotalVolume(aggregate.getTotalVolume() + totalVolume);
            aggregate.setVwap(vwap);
            aggregate.setMinPrice(minPrice);
            aggregate.setMaxPrice(maxPrice);
            aggregate.setNetImbalance(aggregate.getNetImbalance() + calculateImbalance(bucketTrades));

            aggregateRepository.save(aggregate);
        }
    }

    /**
     * Calculate VWAP for a list of trades.
     */
    private BigDecimal calculateVwap(List<DpLpTrade> trades) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal weightedSum = trades.stream()
            .map(trade -> trade.getPrice().multiply(BigDecimal.valueOf(trade.getSize())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalVolume = trades.stream().mapToLong(DpLpTrade::getSize).sum();

        return totalVolume > 0 
            ? weightedSum.divide(BigDecimal.valueOf(totalVolume), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    }

    /**
     * Calculate imbalance (simplified: positive = buying pressure, negative = selling pressure).
     */
    private long calculateImbalance(List<DpLpTrade> trades) {
        // Simplified: sum all volumes (can be enhanced with buy/sell flags)
        return trades.stream().mapToLong(DpLpTrade::getSize).sum();
    }

    /**
     * Get top price levels with dark/lit volume breakdown.
     */
    private List<DpLpSummaryDto.PriceLevel> getTopPriceLevels(
        String ticker, OffsetDateTime startTime, OffsetDateTime endTime, int limit
    ) {
        List<DpLpTrade> trades = tradeRepository.findByTickerAndTsBetweenOrderByTsAsc(
            ticker, startTime, endTime
        );

        Map<BigDecimal, Map<VenueType, Long>> priceVolumeMap = new HashMap<>();

        for (DpLpTrade trade : trades) {
            priceVolumeMap
                .computeIfAbsent(trade.getPrice(), k -> new EnumMap<>(VenueType.class))
                .merge(trade.getVenueType(), (long) trade.getSize(), Long::sum);
        }

        return priceVolumeMap.entrySet().stream()
            .map(entry -> {
                BigDecimal price = entry.getKey();
                Long darkVolume = entry.getValue().getOrDefault(VenueType.DARK, 0L);
                Long litVolume = entry.getValue().getOrDefault(VenueType.LIT, 0L);
                Long netVolume = darkVolume + litVolume;
                return new DpLpSummaryDto.PriceLevel(price, darkVolume, litVolume, netVolume);
            })
            .sorted(Comparator.comparing(DpLpSummaryDto.PriceLevel::netVolume).reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Calculate start time based on window string (e.g., "1d", "5d", "1m").
     */
    private OffsetDateTime calculateStartTime(OffsetDateTime endTime, String window) {
        return switch (window) {
            case "1d" -> endTime.minus(1, ChronoUnit.DAYS);
            case "5d" -> endTime.minus(5, ChronoUnit.DAYS);
            case "1w" -> endTime.minus(7, ChronoUnit.DAYS);
            case "1m" -> endTime.minus(30, ChronoUnit.DAYS);
            case "3m" -> endTime.minus(90, ChronoUnit.DAYS);
            default -> endTime.minus(1, ChronoUnit.DAYS);
        };
    }

    /**
     * Select appropriate bucket size based on window.
     */
    private BucketSize selectBucketSize(String window) {
        return switch (window) {
            case "1d" -> BucketSize.FIVE_MINUTES;
            case "5d", "1w" -> BucketSize.ONE_HOUR;
            case "1m", "3m" -> BucketSize.ONE_DAY;
            default -> BucketSize.FIVE_MINUTES;
        };
    }

    /**
     * Calculate bucket start time for a given timestamp and bucket size.
     */
    private OffsetDateTime bucketStart(OffsetDateTime timestamp, BucketSize bucketSize) {
        return switch (bucketSize) {
            case ONE_MINUTE -> timestamp.truncatedTo(ChronoUnit.MINUTES);
            case FIVE_MINUTES -> timestamp.truncatedTo(ChronoUnit.MINUTES)
                .withMinute(timestamp.getMinute() / 5 * 5);
            case ONE_HOUR -> timestamp.truncatedTo(ChronoUnit.HOURS);
            case ONE_DAY -> timestamp.truncatedTo(ChronoUnit.DAYS);
        };
    }

    /**
     * Calculate dark-to-lit ratio.
     */
    private BigDecimal calculateRatio(Long darkVolume, Long litVolume) {
        if (litVolume == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(darkVolume)
            .divide(BigDecimal.valueOf(litVolume), 4, RoundingMode.HALF_UP);
    }

    /**
     * Convert entity to DTO.
     */
    private DpLpAggregateDto toAggregateDto(DpLpAggregate aggregate) {
        return new DpLpAggregateDto(
            aggregate.getTicker(),
            aggregate.getVenueType(),
            aggregate.getBucketStart(),
            aggregate.getBucketSize(),
            aggregate.getTradeCount(),
            aggregate.getTotalVolume(),
            aggregate.getVwap(),
            aggregate.getMinPrice(),
            aggregate.getMaxPrice(),
            aggregate.getNetImbalance()
        );
    }

    /**
     * Key for grouping trades into buckets.
     */
    private record AggregateKey(String ticker, VenueType venueType, OffsetDateTime timestamp) {}

    private record DailyDarkPoolTradeKey(String ticker, BigDecimal price, LocalDate tradeDate) {}

    private static final class DailyDarkPoolTradeAggregate {

        private DpLpTrade latestTrade;
        private final BigDecimal displayPrice;
        private final List<DpLpTrade> constituentTrades = new ArrayList<>();
        private BigDecimal premium = BigDecimal.ZERO;
        private int totalSize = 0;

        private DailyDarkPoolTradeAggregate(DpLpTrade firstTrade, BigDecimal displayPrice) {
            this.latestTrade = firstTrade;
            this.displayPrice = displayPrice;
        }

        private void add(DpLpTrade trade) {
            constituentTrades.add(trade);
            int size = trade.getSize() != null ? trade.getSize() : 0;
            totalSize += size;
            premium = premium.add(trade.getPrice().multiply(BigDecimal.valueOf(size)));
            if (trade.getTs().isAfter(latestTrade.getTs())) {
                latestTrade = trade;
            }
        }

        private BigDecimal premium() {
            return premium.setScale(2, RoundingMode.HALF_UP);
        }

        private OffsetDateTime latestTimestamp() {
            return latestTrade.getTs();
        }

        private DarkPoolRecentTradeDto toDto() {
            return toDto(latestTrade, totalSize, premium(), displayPrice);
        }

        private List<DarkPoolRecentTradeDto> constituents() {
            return constituentTrades.stream()
                .map(trade -> toDto(
                    trade,
                    trade.getSize() != null ? trade.getSize() : 0,
                    trade.getPrice().multiply(BigDecimal.valueOf(trade.getSize() != null ? trade.getSize() : 0))
                        .setScale(2, RoundingMode.HALF_UP),
                    trade.getPrice().setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();
        }

        private DarkPoolRecentTradeDto toDto(
            DpLpTrade trade,
            int size,
            BigDecimal tradePremium,
            BigDecimal tradePrice
        ) {
            return new DarkPoolRecentTradeDto(
                trade.getTicker(),
                tradePrice,
                size,
                size,
                trade.getTs().toString(),
                tradePremium,
                null,
                null,
                null,
                null,
                trade.getVenue(),
                false,
                null,
                null,
                trade.getTradeId(),
                null,
                null
            );
        }
    }
}
