package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.MarketPricesResponse;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.dto.StockVolumePriceLevelsResponse;
import com.marketmono.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for market data endpoints
 */
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {
    
    private static final Logger log = LoggerFactory.getLogger(MarketDataController.class);
    
    private final MarketDataService marketDataService;
    private final UnusualWhalesClient unusualWhalesClient;
    
    public MarketDataController(MarketDataService marketDataService, UnusualWhalesClient unusualWhalesClient) {
        this.marketDataService = marketDataService;
        this.unusualWhalesClient = unusualWhalesClient;
    }
    
    /**
     * Get market prices for multiple tickers
     * 
     * @param tickers Comma-separated list of ticker symbols
     * @param timeframe Timeframe (1D, 5D, 1M, 3M, 6M, YTD, 1Y, 5Y)
     * @param date Optional date for historical data
     * @return Map of ticker -> market prices response
     */
    @GetMapping("/prices")
    public ResponseEntity<Map<String, MarketPricesResponse>> getMarketPrices(
        @RequestParam("tickers") List<String> tickers,
        @RequestParam("timeframe") String timeframe,
        @RequestParam(value = "date", required = false) LocalDate date
    ) {
        log.info("Fetching market prices for {} tickers with timeframe {}", tickers.size(), timeframe);
        
        Map<String, MarketPricesResponse> result = new HashMap<>();
        
        for (String ticker : tickers) {
            try {
                MarketPricesResponse response = marketDataService.getMarketPrices(ticker, timeframe, date);
                result.put(ticker, response);
            } catch (Exception e) {
                log.error("Failed to fetch prices for {}: {}", ticker, e.getMessage());
                // Return empty result for failed ticker
                result.put(ticker, new MarketPricesResponse(
                    ticker,
                    0,
                    0,
                    true,
                    List.of()
                ));
            }
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get market prices for a single ticker
     * 
     * @param ticker Ticker symbol
     * @param timeframe Timeframe (1D, 5D, 1M, 3M, 6M, YTD, 1Y, 5Y)
     * @param date Optional date for historical data
     * @return Market prices response
     */
    @GetMapping("/prices/{ticker}")
    public ResponseEntity<MarketPricesResponse> getTickerPrices(
        @PathVariable("ticker") String ticker,
        @RequestParam("timeframe") String timeframe,
        @RequestParam(value = "date", required = false) LocalDate date
    ) {
        log.debug("Fetching market prices for {} with timeframe {}", ticker, timeframe);
        
        try {
            MarketPricesResponse response = marketDataService.getMarketPrices(ticker, timeframe, date);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for {}: {}", ticker, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error fetching prices for {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get current stock state (price, volume, prev close) from upstream market data provider
     * Replaces Polygon /v3/snapshot/options/{ticker} endpoint
     * 
     * @param ticker Stock ticker symbol
     * @return Stock state response with current price and volume
     */
    @GetMapping("/stock/{ticker}/state")
    public ResponseEntity<StockStateResponse> getStockState(@PathVariable("ticker") String ticker) {
        log.debug("Fetching stock state for {}", ticker);
        
        try {
            StockStateResponse response = unusualWhalesClient.getStockState(ticker);
            if (response == null) {
                log.warn("No stock state data available for {}", ticker);
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching stock state for {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get stock volume price levels (dark pool & lit pool) from upstream market data provider
     * Replaces Polygon /v3/trades/{ticker} endpoint for DP/LP classification
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Volume price levels response with lit and off-lit (dark pool) volume per price
     */
    @GetMapping("/stock/{ticker}/volume-price-levels")
    public ResponseEntity<StockVolumePriceLevelsResponse> getStockVolumePriceLevels(
        @PathVariable("ticker") String ticker,
        @RequestParam(value = "date", required = false) String date
    ) {
        log.debug("Fetching stock volume price levels for {} on {}", ticker, date != null ? date : "latest");
        
        try {
            StockVolumePriceLevelsResponse response = unusualWhalesClient.getStockVolumePriceLevels(ticker, date);
            if (response == null || response.data() == null) {
                log.warn("No volume price levels data available for {} on {}", ticker, date);
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching volume price levels for {}: {}", ticker, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
