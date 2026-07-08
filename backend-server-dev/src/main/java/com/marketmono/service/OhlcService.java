package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.exception.OhlcRequestException;
import com.marketmono.exception.UnusualWhalesShedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for retrieving OHLC (Open, High, Low, Close) candle data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OhlcService {
    private static final Duration COALESCED_WAIT_TIMEOUT = Duration.ofSeconds(10);
    
    private final UnusualWhalesClient unusualWhalesClient;
    private final ConcurrentMap<OhlcRequestKey, CompletableFuture<UnusualWhalesOhlcDto>> inFlightRequests =
            new ConcurrentHashMap<>();
    
    /**
     * Get OHLC candle data for a ticker with specified candle size
     * 
     * @param ticker Stock ticker symbol
     * @param candleSize Candle size (1m, 5m, 10m, 15m, 30m, 1h, 4h, 1d)
     * @param date Optional date in YYYY-MM-DD format
     * @param timeframe Optional timeframe (1D, 2D, 1W, 2W, 1M, 2M, 1Y, 2Y, YTD)
     * @param endDate Optional end date in YYYY-MM-DD format
     * @param limit Optional limit (max 2500, min 1)
     * @return OHLC data response
     */
    @Cacheable(value = "ohlcCache", 
               key = "'ohlc-' + #ticker + '-' + #candleSize + '-' + (#date ?: 'null') + '-' + (#timeframe ?: 'null') + '-' + (#endDate ?: 'null') + '-' + (#limit ?: 'null')",
               cacheManager = "ohlcCacheManager",
               unless = "#result == null")
    public UnusualWhalesOhlcDto getOhlcData(
            String ticker, 
            String candleSize,
            String date,
            String timeframe,
            String endDate,
            Integer limit) {
        OhlcRequestKey requestKey = new OhlcRequestKey(ticker, candleSize, date, timeframe, endDate, limit);
        CompletableFuture<UnusualWhalesOhlcDto> newRequest = new CompletableFuture<>();
        CompletableFuture<UnusualWhalesOhlcDto> existingRequest = inFlightRequests.putIfAbsent(requestKey, newRequest);

        if (existingRequest != null) {
            log.debug("Coalescing concurrent OHLC request for {}", requestKey);
            return awaitExistingRequest(requestKey, existingRequest);
        }
        
        log.debug("Fetching OHLC data for {} with candleSize={}, date={}, timeframe={}, endDate={}, limit={}", 
                  ticker, candleSize, date, timeframe, endDate, limit);
        
        try {
            UnusualWhalesOhlcDto response = unusualWhalesClient.getOhlcData(
                ticker, candleSize, date, timeframe, endDate, limit
            );
            
            if (response != null && response.data() != null) {
                log.info("Retrieved {} OHLC candles for {} with {} candle size", 
                         response.data().size(), ticker, candleSize);
            } else {
                log.warn("No OHLC data returned from API for {} with {} candle size", ticker, candleSize);
            }
            
            newRequest.complete(response);
            return response;
        } catch (UnusualWhalesShedException e) {
            log.warn("OHLC request shed for {} with {} candle size: {}", ticker, candleSize, e.getMessage());
            newRequest.completeExceptionally(e);
            throw e;
        } catch (Exception e) {
            log.error("Error fetching OHLC data for {} with {} candle size: {}", 
                      ticker, candleSize, e.getMessage(), e);
            RuntimeException wrapped = new OhlcRequestException("Failed to fetch OHLC data: " + e.getMessage(), e);
            newRequest.completeExceptionally(wrapped);
            throw wrapped;
        } finally {
            inFlightRequests.remove(requestKey, newRequest);
        }
    }

    private UnusualWhalesOhlcDto awaitExistingRequest(
            OhlcRequestKey requestKey,
            CompletableFuture<UnusualWhalesOhlcDto> request
    ) {
        try {
            return request.get(COALESCED_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OhlcRequestException("Interrupted while waiting for coalesced OHLC request: " + requestKey, e);
        } catch (TimeoutException e) {
            throw new OhlcRequestException("Timed out waiting for coalesced OHLC request: " + requestKey, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new OhlcRequestException("Coalesced OHLC request failed: " + requestKey, cause);
        }
    }

    private record OhlcRequestKey(
            String ticker,
            String candleSize,
            String date,
            String timeframe,
            String endDate,
            Integer limit
    ) {}
}
