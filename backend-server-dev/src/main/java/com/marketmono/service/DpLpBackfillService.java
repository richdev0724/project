package com.marketmono.service;

import com.marketmono.dto.DpLpBackfillRequest;
import com.marketmono.dto.DpLpBackfillResponse;
import com.marketmono.entity.BucketSize;
import com.marketmono.entity.DpLpTrade;
import com.marketmono.entity.VenueType;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DpLpBackfillService {

    private final DpLpService dpLpService;

    /**
     * Generate synthetic DP/LP trade data for testing and manual backfill.
     * In production, this would fetch real data from Polygon or other sources.
     */
    @Transactional
    public DpLpBackfillResponse backfillData(DpLpBackfillRequest request) {
        OffsetDateTime startTime = OffsetDateTime.now();
        log.info("Starting DP/LP backfill for tickers: {}", request.tickers());

        LocalDate startDate = determineStartDate(request);
        LocalDate endDate = determineEndDate(request);

        log.info("Backfill date range: {} to {}", startDate, endDate);

        List<DpLpTrade> allTrades = new ArrayList<>();
        Map<String, Integer> tickerBreakdown = new HashMap<>();

        for (String ticker : request.tickers()) {
            List<DpLpTrade> tickerTrades = generateTradesForTicker(
                ticker, startDate, endDate
            );
            allTrades.addAll(tickerTrades);
            tickerBreakdown.put(ticker, tickerTrades.size());
        }

        // Save trades and trigger aggregation
        dpLpService.saveTrades(allTrades);

        OffsetDateTime endTime = OffsetDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        // Count aggregates created (approximate: trades * bucket sizes)
        int aggregatesCreated = allTrades.size() * BucketSize.values().length;

        log.info("Backfill completed: {} trades, ~{} aggregates, {}ms",
            allTrades.size(), aggregatesCreated, durationMs);

        return new DpLpBackfillResponse(
            "SUCCESS",
            allTrades.size(),
            aggregatesCreated,
            tickerBreakdown,
            startTime,
            endTime,
            durationMs,
            String.format("Backfilled %d trades for %d tickers",
                allTrades.size(), request.tickers().size())
        );
    }

    /**
     * Generate synthetic trades for a ticker over a date range.
     * In production, replace with real API calls to Polygon/upstream market data provider.
     */
    private List<DpLpTrade> generateTradesForTicker(
        String ticker, LocalDate startDate, LocalDate endDate
    ) {
        List<DpLpTrade> trades = new ArrayList<>();
        Random random = ThreadLocalRandom.current();

        // Generate base price for ticker
        BigDecimal basePrice = BigDecimal.valueOf(100L + random.nextInt(400));

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Market hours: 9:30 AM - 4:00 PM ET (13:30 - 20:00 UTC)
            ZonedDateTime marketOpen = currentDate.atTime(13, 30)
                .atZone(ZoneId.of("UTC"));
            ZonedDateTime marketClose = currentDate.atTime(20, 0)
                .atZone(ZoneId.of("UTC"));

            // Generate trades throughout the day
            int tradesPerDay = 50 + random.nextInt(100);
            for (int i = 0; i < tradesPerDay; i++) {
                // Random time during market hours
                long secondsInDay = Duration.between(marketOpen, marketClose).getSeconds();
                long randomSeconds = random.nextLong(secondsInDay);
                OffsetDateTime tradeTime = marketOpen.plusSeconds(randomSeconds)
                    .toOffsetDateTime();

                // Price variation +/- 5%
                BigDecimal priceVariation = BigDecimal.valueOf(
                    -5 + random.nextDouble() * 10
                );
                BigDecimal price = basePrice.add(
                    basePrice.multiply(priceVariation.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                );

                // Random volume: 100 - 10,000 shares
                int size = 100 + random.nextInt(9900);

                // 60% dark pool, 40% lit pool
                VenueType venueType = random.nextDouble() < 0.6
                    ? VenueType.DARK : VenueType.LIT;

                String venue = venueType == VenueType.DARK
                    ? selectDarkVenue(random)
                    : selectLitVenue(random);

                DpLpTrade trade = DpLpTrade.builder()
                    .ticker(ticker)
                    .venueType(venueType)
                    .price(price.setScale(4, RoundingMode.HALF_UP))
                    .size(size)
                    .ts(tradeTime)
                    .venue(venue)
                    .ingestSource("MANUAL_BACKFILL")
                    .build();

                trades.add(trade);
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Generated {} synthetic trades for {}", trades.size(), ticker);
        return trades;
    }

    /**
     * Select random dark pool venue.
     */
    private String selectDarkVenue(Random random) {
        String[] darkVenues = {
            "UBSA", "JPMS", "MSPL", "CROS", "DBAX",
            "BATS", "IEXD", "SGMT", "LEHM", "JPMX"
        };
        return darkVenues[random.nextInt(darkVenues.length)];
    }

    /**
     * Select random lit pool venue (exchanges).
     */
    private String selectLitVenue(Random random) {
        String[] litVenues = {
            "NYSE", "NASDAQ", "ARCA", "BATS", "IEX",
            "EDGA", "EDGX", "BYX", "BZX", "PSX"
        };
        return litVenues[random.nextInt(litVenues.length)];
    }

    /**
     * Determine start date from request.
     */
    private LocalDate determineStartDate(DpLpBackfillRequest request) {
        if (request.startDate() != null) {
            return request.startDate();
        }
        if (request.daysBack() != null) {
            return DateUtils.getCurrentDateInEasternTime().minusDays(request.daysBack());
        }
        return DateUtils.getCurrentDateInEasternTime().minusDays(7); // Default: last 7 days
    }

    /**
     * Determine end date from request.
     */
    private LocalDate determineEndDate(DpLpBackfillRequest request) {
        if (request.endDate() != null) {
            return request.endDate();
        }
        return DateUtils.getCurrentDateInEasternTime(); // Default: today
    }
}
