package com.marketmono.client;

import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.config.UnusualWhalesShed503Properties;
import com.marketmono.dto.CongressTradesResponse;
import com.marketmono.dto.DarkPoolRecentResponseDto;
import com.marketmono.dto.EarningsDetailedResponseDto;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import com.marketmono.dto.GreekExposureByExpiryDto;
import com.marketmono.dto.GreekExposureByStrikeDto;
import com.marketmono.dto.GreekExposureByStrikeExpiryDto;
import com.marketmono.dto.GreekExposureResponseDto;
import com.marketmono.dto.OptionContractHistoricResponseDto;
import com.marketmono.dto.OptionContractIntradayResponseDto;
import com.marketmono.dto.OptionContractsAllResponseDto;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.dto.OptionsVolumeDto;
import com.marketmono.dto.PoliticianPortfoliosResponse;
import com.marketmono.dto.PoliticianTradesResponse;
import com.marketmono.dto.PoliticiansListResponse;
import com.marketmono.dto.SpotExposureResponseDto;
import com.marketmono.dto.StockMaxPainResponse;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.dto.StockInformationResponse;
import com.marketmono.dto.StockVolumePriceLevelsResponse;
import com.marketmono.dto.StockPriceLevelsResponseDto;
import com.marketmono.dto.InsiderTransactionsResponseDto;
import com.marketmono.dto.InstitutionalOwnershipResponseDto;
import com.marketmono.dto.EtfHoldingsResponseDto;
import com.marketmono.dto.IvRankResponseDto;
import com.marketmono.dto.RealizedVolatilityResponseDto;
import com.marketmono.dto.TickerEarningsResponseDto;
import com.marketmono.dto.AnalystRatingsResponseDto;
import com.marketmono.dto.OptionChainsResponseDto;
import com.marketmono.dto.PriceChangePerMonthResponseDto;
import com.marketmono.dto.UnusualWhalesTopNetImpactDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.dto.UnusualWhalesMarketTideDto;
import com.marketmono.dto.UnusualWhalesSpyOhlcDto;
import com.marketmono.dto.UnusualWhalesTickerTideDto;
import com.marketmono.dto.UnusualWhalesTickerOhlcDto;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.metrics.UwCallSourceContext;
import com.marketmono.service.RateLimitService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * HTTP client for the upstream market data API with circuit breaker and rate limit tracking
 * Checks rate limits before making requests to prevent unnecessary API calls
 */
@Component
public class UnusualWhalesClient {
    
    private static final Logger log = LoggerFactory.getLogger(UnusualWhalesClient.class);
    private static final String FLOW_ALERTS_ENDPOINT = "/api/option-trades/flow-alerts";
    
    // Operation names for rate limiting and logging
    private static final String FLOW_ALERTS_OP = "flow alerts";
    private static final String MARKET_TIDE_OP = "market tide";
    private static final String TOP_NET_IMPACT_OP = "top net impact";
    private static final String SPY_OHLC_OP = "SPY OHLC";
    private static final String TICKER_TIDE_OP = "ticker tide";
    private static final String TICKER_OHLC_OP = "ticker OHLC";
    private static final String STOCK_MAX_PAIN_OP = "stock max pain";
    private static final String STOCK_STATE_OP = "stock state";
    private static final String STOCK_VOLUME_PRICE_LEVELS_OP = "stock volume price levels";
    private static final String POLITICIANS_LIST_OP = "politicians list";
    private static final String POLITICIAN_TRADES_OP = "politician trades";
    private static final String POLITICIAN_PORTFOLIOS_OP = "politician portfolios";
    private static final String CONGRESS_TRADES_OP = "congress trades";
    private static final String GREEK_EXPOSURE_BY_STRIKE_OP = "greek exposure by strike";
    private static final String GREEK_EXPOSURE_BY_EXPIRY_OP = "greek exposure by expiry";
    private static final String GREEK_EXPOSURE_BY_STRIKE_EXPIRY_OP = "greek exposure by strike expiry";
    private static final String EARNINGS_PREMARKET_OP = "earnings premarket";
    private static final String EARNINGS_AFTERHOURS_OP = "earnings afterhours";
    private static final String EARNINGS_TICKER_OP = "earnings ticker";
    private static final String OHLC_DATA_OP = "OHLC data";
    private static final String DARKPOOL_RECENT_OP = "darkpool recent";
    private static final String DARKPOOL_TICKER_OP = "darkpool ticker";
    private static final String OPTION_CONTRACTS_OP = "option contracts screener";
    private static final String OPTIONS_VOLUME_OP = "options volume";
    private static final String SPOT_EXPOSURE_BY_STRIKE_OP = "spot exposure by strike";
    private static final String SPOT_EXPOSURE_BY_EXPIRY_STRIKE_OP = "spot exposure by expiry strike";
    private static final String INSIDER_TRANSACTIONS_OP = "insider transactions";
    private static final String ANALYST_RATINGS_OP = "analyst ratings";
    private static final String OPTION_CHAINS_OP = "option chains";
    private static final String EXPIRY_BREAKDOWN_OP = "expiry breakdown";
    private static final String PRICE_CHANGE_PER_MONTH_OP = "price change per month";
    private static final String STOCK_INFORMATION_OP = "stock information";
    private static final String OPTION_CONTRACT_HISTORIC_OP = "option contract historic";
    private static final String OPTION_CONTRACT_INTRADAY_OP = "option contract intraday";
    private static final String OPTION_CONTRACTS_ALL_OP = "option contracts all";
    private static final String IV_RANK_OP = "iv rank";
    private static final String REALIZED_VOLATILITY_OP = "realized volatility";
    private static final String OPTIONS_PRICE_LEVELS_OP = "options price levels";
    private static final String INSTITUTIONAL_OWNERSHIP_OP = "institutional ownership";
    private static final String ETF_HOLDINGS_OP = "etf holdings";
    
    // API path constants
    private static final String STOCK_API_PATH = "/api/stock/";
    private static final long REQUEST_BUDGET_WINDOW_MS = 60_000L;
    private static final String MARKET_DATA_REQUESTS_RESERVED_METRIC = "marketmono.market_data.requests.reserved";
    private static final String MARKET_DATA_REQUESTS_COMPLETED_METRIC = "marketmono.market_data.requests.completed";
    private static final String MARKET_DATA_REQUESTS_REJECTED_METRIC = "marketmono.market_data.requests.rejected";
    private static final String MARKET_DATA_AVAILABLE_PERMITS_METRIC = "marketmono.market_data.available.permits";
    private static final String MARKET_DATA_BUDGET_ROLLING_USAGE_METRIC =
        "marketmono.market_data.budget.rolling.minute.usage";
    private static final String MARKET_DATA_BUDGET_TARGET_METRIC = "marketmono.market_data.budget.target.per.minute";
    private static final String MARKET_DATA_BUDGET_HARD_CAP_METRIC =
        "marketmono.market_data.budget.hard.cap.per.minute";
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 2;
    private static final int MAX_RETRY_AFTER_SECONDS = 65;
    
    private final WebClient webClient;
    private final UnusualWhalesConfig config;
    private final UnusualWhalesShed503Properties shed503Properties;
    private final CircuitBreaker circuitBreaker;
    private final RateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;
    private final UnusualWhalesCallReporter callReporter;
    private final Object requestBudgetLock = new Object();
    private final Deque<Long> requestBudgetWindow = new ArrayDeque<>();
    
    // Global in-process concurrency limiter for the upstream market data API.
    private final Semaphore concurrencyLimiter;
    
    // Self-injection for AOP proxy access (setter injection to avoid circular dependency)
    private UnusualWhalesClient self;
    
    @Autowired
    @Lazy
    void setSelf(UnusualWhalesClient self) { this.self = self; }
    
    private UnusualWhalesClient proxy() { return (self != null) ? self : this; }
    
    @Autowired
    public UnusualWhalesClient(
        UnusualWhalesConfig config,
        CircuitBreaker unusualWhalesCircuitBreaker,
        RateLimitService rateLimitService,
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        UnusualWhalesShed503Properties shed503Properties,
        UnusualWhalesCallReporter callReporter
    ) {
        this(
            config,
            unusualWhalesCircuitBreaker,
            rateLimitService,
            meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null,
            shed503Properties,
            callReporter
        );
    }

    UnusualWhalesClient(
        UnusualWhalesConfig config, 
        CircuitBreaker unusualWhalesCircuitBreaker,
        RateLimitService rateLimitService
    ) {
        this(config, unusualWhalesCircuitBreaker, rateLimitService, (MeterRegistry) null);
    }

    UnusualWhalesClient(
        UnusualWhalesConfig config,
        CircuitBreaker unusualWhalesCircuitBreaker,
        RateLimitService rateLimitService,
        MeterRegistry meterRegistry
    ) {
        this(
            config,
            unusualWhalesCircuitBreaker,
            rateLimitService,
            meterRegistry,
            new UnusualWhalesShed503Properties()
        );
    }

    UnusualWhalesClient(
        UnusualWhalesConfig config,
        CircuitBreaker unusualWhalesCircuitBreaker,
        RateLimitService rateLimitService,
        MeterRegistry meterRegistry,
        UnusualWhalesShed503Properties shed503Properties
    ) {
        this(
            config,
            unusualWhalesCircuitBreaker,
            rateLimitService,
            meterRegistry,
            shed503Properties,
            UnusualWhalesCallReporter.noop()
        );
    }

    UnusualWhalesClient(
        UnusualWhalesConfig config,
        CircuitBreaker unusualWhalesCircuitBreaker,
        RateLimitService rateLimitService,
        MeterRegistry meterRegistry,
        UnusualWhalesShed503Properties shed503Properties,
        UnusualWhalesCallReporter callReporter
    ) {
        this.config = config;
        this.shed503Properties = shed503Properties != null ? shed503Properties : new UnusualWhalesShed503Properties();
        this.circuitBreaker = unusualWhalesCircuitBreaker;
        this.rateLimitService = rateLimitService;
        this.meterRegistry = meterRegistry;
        this.callReporter = callReporter != null ? callReporter : UnusualWhalesCallReporter.noop();
        this.concurrencyLimiter = new Semaphore(Math.max(1, config.getApi().getMaxConcurrentRequests()), true);
        registerUsageGauges();
        
        String baseUrl = config.getApi().getBaseUrl();
        String apiKey = config.getApi().getApiKey();
        log.info(
            "Initializing market data client - baseUrl: {}, apiKey: {}, maxConcurrentRequests: {}, permitWaitMs: {}, targetPerMinute: {}, hardCapPerMinute: {}",
            baseUrl,
            apiKey != null ? "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "null",
            concurrencyLimiter.availablePermits(),
            permitWaitTimeoutMs(),
            config.getApi().getRateLimit().getTargetPerMinute(),
            config.getApi().getRateLimit().getHardCapPerMinute()
        );
        
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer for large responses
            .build();
    }
    
    /**
     * Execute API call with rate limit checking and tracking
     * Checks rate limit before making request, records success/failure after
     * 
     * @param apiCall The API call to execute
     * @return Response from API call, or null if rate limit exceeded
     */
    private <T> T executeWithRateLimitCheck(
        String operationName,
        String endpoint,
        java.util.function.Supplier<ApiCallResult<T>> apiCall
    ) {
        if (!restApiEnabled(operationName)) {
            log.warn("REST API disabled for: {}", operationName);
            recordRejected(operationName, "disabled");
            return null;
        }
        boolean permitAcquired = false;
        long startNanos = System.nanoTime();
        try {
            int permitWaitMs = permitWaitTimeoutMs();
            log.trace("Acquiring concurrency permit for {} (available: {}, waitMs: {})",
                operationName, concurrencyLimiter.availablePermits(), permitWaitMs);
            permitAcquired = concurrencyLimiter.tryAcquire(permitWaitMs, TimeUnit.MILLISECONDS);
            if (!permitAcquired) {
                log.warn("Market data concurrency exhausted for {} after {}ms (available permits: {})",
                    operationName, permitWaitMs, concurrencyLimiter.availablePermits());
                recordRejected(operationName, "concurrency");
                throwShedIfEnabled(operationName, "concurrency", DEFAULT_RETRY_AFTER_SECONDS);
                return null;
            }
            log.trace("Acquired concurrency permit for {}", operationName);
            
            // Execute API call
            ApiCallResult<T> result = apiCall.get();
            if (result == null) {
                recordCompleted(operationName, "empty");
                return null;
            }
            recordCompleted(operationName, result.outcome());
            reportCall(operationName, endpoint, result, elapsedMs(startNanos));

            if (!result.successful()) {
                throwForShedResultIfEnabled(operationName, result);
            }

            if (result.successful()) {
                rateLimitService.recordSuccessfulRequest(result.headers());
            }
            
            return result.payload();
        } catch (RateLimitExceededException e) {
            // Rate limit exceeded or endpoint blocked - don't make request
            log.warn(
                "Market data request blocked before upstream call - operation: {}, errorClass: {}",
                operationName,
                e.getClass().getSimpleName()
            );
            throwShedIfEnabled(operationName, "budget", budgetRetryAfterSeconds());
            return null;
        } catch (WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 429) {
                // 429 response - record rate limit hit
                rateLimitService.recordRateLimitHit(e.getHeaders());
                log.error("Received 429 rate limit response from upstream market data API");
            } else if (statusCode == 422) {
                // 422 response - endpoint blocked (enterprise-only, access denied, etc.)
                rateLimitService.recordBlockedEndpoint(422, classify422Reason(e));
                log.error(
                    "Endpoint blocked by upstream market data API - operation: {}, status: {}, errorClass: {}",
                    operationName,
                    statusCode,
                    e.getClass().getSimpleName()
                );
            }
            recordCompleted(operationName, outcomeForStatus(statusCode));
            reportCall(
                operationName,
                endpoint,
                new ApiCallResult<>(null, e.getHeaders(), false, outcomeForStatus(statusCode),
                    statusCode, "WebClientResponseException", true),
                elapsedMs(startNanos)
            );
            if (statusCode == 429) {
                throwShedIfEnabled(operationName, "upstream_429", retryAfterSeconds(e.getHeaders()));
            }
            return null; // Return null instead of throwing since method returns T
        } catch (InterruptedException interruptedException) {
            // Concurrency limiter interrupted
            Thread.currentThread().interrupt();
            log.warn("Request interrupted while waiting for concurrency permit: {}", operationName);
            recordRejected(operationName, "interrupted");
            throwShedIfEnabled(operationName, "interrupted", DEFAULT_RETRY_AFTER_SECONDS);
            return null;
        } finally {
            if (permitAcquired) {
                concurrencyLimiter.release();
                log.trace("Released concurrency permit for {} (available: {})", operationName, concurrencyLimiter.availablePermits());
            }
        }
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    private void reportCall(
        String operationName,
        String endpoint,
        ApiCallResult<?> result,
        long durationMs
    ) {
        if (!result.reportable()) {
            return;
        }
        try {
            callReporter.report(new UnusualWhalesCallReporter.CallReport(
                endpoint,
                "BACKEND_SERVER",
                operationName,
                "GET",
                result.httpStatus(),
                durationMs,
                result.errorMessage(),
                headerInt(result.headers(), "x-uw-daily-req-count"),
                headerInt(result.headers(), "x-uw-token-req-limit")
            ));
        } catch (RuntimeException e) {
            log.warn(
                "Market data call reporting failed without affecting upstream response - operation: {}, endpoint: {}, errorClass: {}",
                operationName,
                endpoint,
                e.getClass().getSimpleName()
            );
        }
    }

    private Integer headerInt(HttpHeaders headers, String headerName) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private int permitWaitTimeoutMs() {
        return Math.max(0, config.getApi().getTimeout().getPermitWait());
    }

    public void recordRateLimitPreflightRejection(String operationName) {
        recordRejected(operationName, "stored_quota");
    }

    public boolean isRequestBudgetAboveTarget() {
        int targetPerMinute = config.getApi().getRateLimit().getTargetPerMinute();
        return targetPerMinute > 0 && currentRollingMinuteBudgetUsage() >= targetPerMinute;
    }

    private boolean reserveRequestBudget(String operationName) {
        int hardCapPerMinute = config.getApi().getRateLimit().getHardCapPerMinute();
        if (hardCapPerMinute <= 0) {
            recordReserved(operationName);
            return true;
        }

        long now = System.currentTimeMillis();
        int usage;
        synchronized (requestBudgetLock) {
            pruneRequestBudgetWindow(now);
            if (requestBudgetWindow.size() >= hardCapPerMinute) {
                log.warn("Market data request budget exhausted for {}: {}/{} requests in the rolling minute",
                    operationName, requestBudgetWindow.size(), hardCapPerMinute);
                recordRejected(operationName, "budget");
                return false;
            }
            requestBudgetWindow.addLast(now);
            usage = requestBudgetWindow.size();
        }
        recordReserved(operationName);

        int targetPerMinute = config.getApi().getRateLimit().getTargetPerMinute();
        if (targetPerMinute > 0 && usage > targetPerMinute) {
            log.warn("Market data request budget above target after {}: {}/{} requests in the rolling minute (hard cap {})",
                operationName, usage, targetPerMinute, hardCapPerMinute);
        }
        return true;
    }

    private void registerUsageGauges() {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder(MARKET_DATA_AVAILABLE_PERMITS_METRIC, concurrencyLimiter, Semaphore::availablePermits)
            .description("Available in-process concurrency permits for upstream market data API calls")
            .register(meterRegistry);
        Gauge.builder(MARKET_DATA_BUDGET_ROLLING_USAGE_METRIC, this, UnusualWhalesClient::rollingMinuteBudgetUsage)
            .description("In-process market data requests reserved in the last rolling minute")
            .register(meterRegistry);
        Gauge.builder(
                MARKET_DATA_BUDGET_TARGET_METRIC,
                this,
                client -> client.config.getApi().getRateLimit().getTargetPerMinute()
            )
            .description("Configured per-node market data target request budget per minute")
            .register(meterRegistry);
        Gauge.builder(
                MARKET_DATA_BUDGET_HARD_CAP_METRIC,
                this,
                client -> client.config.getApi().getRateLimit().getHardCapPerMinute()
            )
            .description("Configured per-node market data hard request budget per minute")
            .register(meterRegistry);
    }

    private double rollingMinuteBudgetUsage() {
        return currentRollingMinuteBudgetUsage();
    }

    private int currentRollingMinuteBudgetUsage() {
        synchronized (requestBudgetLock) {
            pruneRequestBudgetWindow(System.currentTimeMillis());
            return requestBudgetWindow.size();
        }
    }

    private void recordReserved(String operationName) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(MARKET_DATA_REQUESTS_RESERVED_METRIC)
            .description("Market data API requests reserved by the local rolling-minute budget")
            .tag("operation", metricOperation(operationName))
            .tag("source", metricSource())
            .register(meterRegistry)
            .increment();
    }

    private void recordCompleted(String operationName, String result) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(MARKET_DATA_REQUESTS_COMPLETED_METRIC)
            .description("Market data logical client calls completed by operation and result")
            .tag("operation", metricOperation(operationName))
            .tag("source", metricSource())
            .tag("result", result != null ? result : "unknown")
            .register(meterRegistry)
            .increment();
    }

    private void recordRejected(String operationName, String reason) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(MARKET_DATA_REQUESTS_REJECTED_METRIC)
            .description("Market data API requests rejected before reaching upstream")
            .tag("operation", metricOperation(operationName))
            .tag("source", metricSource())
            .tag("reason", reason != null ? reason : "unknown")
            .register(meterRegistry)
            .increment();
    }

    private String metricOperation(String operationName) {
        return operationName != null ? operationName : "unknown";
    }

    private String metricSource() {
        return UwCallSourceContext.current().metricValue();
    }

    private String outcomeForStatus(int statusCode) {
        return switch (statusCode) {
            case 429 -> "http_429";
            case 422 -> "http_422";
            default -> "http_error";
        };
    }

    private void reserveRequestBudgetOrThrow(String operationName) {
        if (!reserveRequestBudget(operationName)) {
            throw new RateLimitExceededException(
                "Market data per-node minute request budget exhausted for " + operationName
            );
        }
    }

    private void pruneRequestBudgetWindow(long now) {
        long cutoff = now - REQUEST_BUDGET_WINDOW_MS;
        while (!requestBudgetWindow.isEmpty() && requestBudgetWindow.peekFirst() <= cutoff) {
            requestBudgetWindow.removeFirst();
        }
    }

    private boolean restApiEnabled(String operationName) {
        if (!config.getApi().isRestEnabled()) {
            log.warn("Market data REST API globally disabled via configuration; skipping {}", operationName);
            return false;
        }
        
        // Check endpoint-specific permission
        return switch (operationName) {
            case CONGRESS_TRADES_OP -> config.getApi().getEndpoints().isCongressTrades();
            case POLITICIAN_TRADES_OP -> config.getApi().getEndpoints().isPoliticianTrades();
            case POLITICIAN_PORTFOLIOS_OP -> config.getApi().getEndpoints().isPoliticianPortfolios();
            case POLITICIANS_LIST_OP -> config.getApi().getEndpoints().isPoliticiansList();
            case FLOW_ALERTS_OP -> config.getApi().getEndpoints().isFlowAlerts();
            case MARKET_TIDE_OP -> config.getApi().getEndpoints().isMarketTide();
            case TOP_NET_IMPACT_OP -> config.getApi().getEndpoints().isTopNetImpact();
            case SPY_OHLC_OP -> config.getApi().getEndpoints().isSpyOhlc();
            case TICKER_TIDE_OP -> config.getApi().getEndpoints().isTickerTide();
            case TICKER_OHLC_OP -> config.getApi().getEndpoints().isTickerOhlc();
            case STOCK_MAX_PAIN_OP -> true; // Stock max pain endpoint always enabled
            case STOCK_STATE_OP -> config.getApi().getEndpoints().isStockState();
            case STOCK_VOLUME_PRICE_LEVELS_OP -> config.getApi().getEndpoints().isStockVolumePriceLevels();
            case GREEK_EXPOSURE_BY_STRIKE_OP -> config.getApi().getEndpoints().isGreekExposure();
            case GREEK_EXPOSURE_BY_EXPIRY_OP -> config.getApi().getEndpoints().isGreekExposure();
            case GREEK_EXPOSURE_BY_STRIKE_EXPIRY_OP -> config.getApi().getEndpoints().isGreekExposure();
            case EARNINGS_PREMARKET_OP, EARNINGS_AFTERHOURS_OP, EARNINGS_TICKER_OP -> true; // Earnings endpoints always enabled
            case OHLC_DATA_OP -> config.getApi().getEndpoints().isTickerOhlc();
            case DARKPOOL_RECENT_OP -> config.getApi().getEndpoints().isDarkpoolRecent();
            case DARKPOOL_TICKER_OP -> config.getApi().getEndpoints().isDarkpoolRecent(); // Use same config as darkpool recent
            case OPTION_CONTRACTS_OP -> config.getApi().getEndpoints().isOptionContracts();
            case OPTIONS_VOLUME_OP -> true; // Options volume endpoint always enabled
            case SPOT_EXPOSURE_BY_STRIKE_OP -> config.getApi().getEndpoints().isSpotExposure();
            case SPOT_EXPOSURE_BY_EXPIRY_STRIKE_OP -> config.getApi().getEndpoints().isSpotExposure();
            case INSIDER_TRANSACTIONS_OP -> true; // Insider transactions endpoint always enabled
            case ANALYST_RATINGS_OP -> true; // Analyst ratings endpoint always enabled
            case OPTION_CHAINS_OP -> true; // Option chains endpoint always enabled
            case EXPIRY_BREAKDOWN_OP -> true; // Expiry breakdown endpoint always enabled
            case PRICE_CHANGE_PER_MONTH_OP -> true; // Price change per month endpoint always enabled
            case STOCK_INFORMATION_OP -> true; // Stock information endpoint always enabled
            case OPTION_CONTRACT_HISTORIC_OP -> true; // Option contract historic endpoint always enabled
            case OPTION_CONTRACT_INTRADAY_OP -> true; // Option contract intraday endpoint always enabled
            case OPTION_CONTRACTS_ALL_OP -> true; // Option contracts all endpoint always enabled
            case IV_RANK_OP -> true; // IV rank endpoint always enabled
            case REALIZED_VOLATILITY_OP -> true; // Realized volatility endpoint always enabled
            case OPTIONS_PRICE_LEVELS_OP -> true; // Options price levels endpoint always enabled
            case INSTITUTIONAL_OWNERSHIP_OP -> true;
            case ETF_HOLDINGS_OP -> true;
            default -> {
                log.warn("Unknown operation name '{}', defaulting to disabled", operationName);
                yield false;
            }
        };
       
    }

    /**
     * Create simple retry strategy for transient errors (not rate limits or circuit breaker)
     * Circuit breaker handles rate limit coordination - don't retry when circuit is OPEN
     * - Retry count is configuration-backed and defaults to 0 to keep widget calls below edge timeout
     * - For 5xx (server errors): configurable backoff
     * - Fail fast on circuit breaker exceptions (CallNotPermittedException)
     */
    private Retry createSimpleRetry() {
        int maxAttempts = config.getApi().getRetry().getMaxAttempts();
        if (maxAttempts <= 0) {
            return Retry.max(0);
        }

        long backoffSeconds = Math.max(1, config.getApi().getRetry().getBackoffMultiplier());
        return Retry.backoff(maxAttempts, Duration.ofSeconds(backoffSeconds))
            .filter(this::shouldRetry)
            .doBeforeRetry(signal -> 
                log.trace("Retrying market data request after {}", retryFailureSummary(signal.failure()))
            );
    }

    private String retryFailureSummary(Throwable failure) {
        if (failure instanceof WebClientResponseException e) {
            return "WebClientResponseException status=" + e.getStatusCode().value();
        }
        return failure != null ? failure.getClass().getSimpleName() : "unknown";
    }

    private String classify422Reason(WebClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (isAccessBlocked422Body(responseBody)) {
            return RateLimitService.MARKET_DATA_HTTP_422_ACCESS_BLOCKED;
        }
        return RateLimitService.MARKET_DATA_HTTP_422_VALIDATION_ERROR;
    }

    private boolean isAccessBlocked422Body(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String normalizedBody = responseBody.toLowerCase(Locale.ROOT);
        return normalizedBody.contains("enterprise only endpoint")
            || normalizedBody.contains("enterprise-only endpoint")
            || normalizedBody.contains("missing access")
            || normalizedBody.contains("access denied")
            || normalizedBody.contains("not authorized");
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return false;
        }
        if (throwable instanceof WebClientResponseException e) {
            int status = e.getStatusCode().value();
            return status >= 500;
        }
        return isTransientNetworkException(throwable);
    }

    private boolean isTransientNetworkException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof TimeoutException
            || throwable instanceof SocketTimeoutException
            || throwable instanceof ConnectException) {
            return true;
        }
        if (throwable instanceof WebClientRequestException) {
            return isTransientNetworkException(throwable.getCause());
        }
        Throwable cause = throwable.getCause();
        return cause != throwable && isTransientNetworkException(cause);
    }

    private record ApiCallResult<T>(
        T payload,
        HttpHeaders headers,
        boolean successful,
        String outcome,
        Integer httpStatus,
        String errorMessage,
        boolean reportable
    ) {
        private ApiCallResult(T payload, HttpHeaders headers, boolean successful) {
            this(payload, headers, successful, successful ? "success" : "error", null, null, true);
        }

        private ApiCallResult(
            T payload,
            HttpHeaders headers,
            boolean successful,
            String outcome,
            Integer httpStatus,
            String errorMessage
        ) {
            this(payload, headers, successful, outcome, httpStatus, errorMessage, true);
        }
    }

    /**
     * Centralized error handling for API calls
     * Processes WebClientResponseException and returns appropriate ApiCallResult
     */
    private <T> ApiCallResult<T> handleApiError(String operationName, WebClientResponseException e) {
        int statusCode = e.getStatusCode().value();
        log.error(
            "Upstream market data API error - operation: {}, status: {}, errorClass: {}",
            operationName,
            statusCode,
            e.getClass().getSimpleName()
        );
        if (statusCode == 429) {
            rateLimitService.recordRateLimitHit(e.getHeaders());
            log.info("Rate limit exhausted for {}", operationName);
            return new ApiCallResult<>(
                null,
                e.getHeaders(),
                false,
                "upstream_429",
                statusCode,
                "WebClientResponseException"
            );
        } else if (statusCode == 422) {
            rateLimitService.recordBlockedEndpoint(422, classify422Reason(e));
            log.error(
                "Endpoint blocked by upstream market data API - operation: {}, status: {}, errorClass: {}",
                operationName,
                statusCode,
                e.getClass().getSimpleName()
            );
            return new ApiCallResult<>(
                null,
                e.getHeaders(),
                false,
                "http_422",
                statusCode,
                "WebClientResponseException"
            );
        } else {
            log.trace("HTTP {} fetching {}", e.getStatusCode(), operationName);
            return new ApiCallResult<>(
                null,
                e.getHeaders(),
                false,
                "http_error",
                statusCode,
                "WebClientResponseException"
            );
        }
    }

    /**
     * Centralized error handling for wrapped exceptions
     * Processes nested WebClientResponseException and returns appropriate ApiCallResult
     */
    private <T> ApiCallResult<T> handleWrappedApiError(String operationName, Exception e) {
        log.error(
            "Upstream market data API wrapped error - operation: {}, errorClass: {}",
            operationName,
            e.getClass().getName()
        );
        if (e.getCause() != null) {
            log.error(
                "Upstream market data API wrapped error cause - operation: {}, causeClass: {}",
                operationName,
                e.getCause().getClass().getName()
            );
        }
        WebClientResponseException we = unwrapWebClientException(e);
        if (we != null) {
            int statusCode = we.getStatusCode().value();
            log.error(
                "Upstream market data API wrapped response error - operation: {}, status: {}, errorClass: {}",
                operationName,
                statusCode,
                we.getClass().getSimpleName()
            );
            if (statusCode == 429) {
                rateLimitService.recordRateLimitHit(we.getHeaders());
                log.info("Rate limit exhausted for {}", operationName);
                return new ApiCallResult<>(
                    null,
                    we.getHeaders(),
                    false,
                    "upstream_429",
                    statusCode,
                    "WebClientResponseException"
                );
            } else if (statusCode == 422) {
                rateLimitService.recordBlockedEndpoint(422, classify422Reason(we));
                log.error(
                    "Endpoint blocked by upstream market data API - operation: {}, status: {}, errorClass: {}",
                    operationName,
                    statusCode,
                    we.getClass().getSimpleName()
                );
                return new ApiCallResult<>(
                    null,
                    we.getHeaders(),
                    false,
                    "http_422",
                    statusCode,
                    "WebClientResponseException"
                );
            }
        }
        if (e instanceof RateLimitExceededException || e.getCause() instanceof RateLimitExceededException) {
            return new ApiCallResult<>(null, null, false, "budget", null, null, false);
        }
        if (e instanceof CallNotPermittedException || e.getCause() instanceof CallNotPermittedException) {
            return new ApiCallResult<>(null, null, false, "circuit_open", null, null, false);
        }
        return new ApiCallResult<>(null, null, false, "error", null, e.getClass().getSimpleName(), true);
    }

    private void throwForShedResultIfEnabled(String operationName, ApiCallResult<?> result) {
        if ("upstream_429".equals(result.outcome())) {
            throwShedIfEnabled(operationName, "upstream_429", retryAfterSeconds(result.headers()));
        } else if ("circuit_open".equals(result.outcome())) {
            throwShedIfEnabled(operationName, "circuit_open", circuitOpenRetryAfterSeconds());
        } else if ("budget".equals(result.outcome())) {
            throwShedIfEnabled(operationName, "budget", budgetRetryAfterSeconds());
        }
    }

    private void throwShedIfEnabled(String operationName, String reason, int retryAfterSeconds) {
        if (shed503Properties.isEnabled()) {
            throw new UnusualWhalesShedException(operationName, reason, retryAfterSeconds);
        }
    }

    private int retryAfterSeconds(HttpHeaders headers) {
        if (headers == null) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        try {
            return boundedRetryAfter(Integer.parseInt(retryAfter.trim()));
        } catch (NumberFormatException ignored) {
            try {
                long seconds = Duration.between(
                    Instant.now(),
                    DateTimeFormatter.RFC_1123_DATE_TIME.parse(retryAfter, Instant::from)
                ).toSeconds();
                return boundedRetryAfter((int) Math.ceil(seconds));
            } catch (DateTimeParseException ignoredDate) {
                return DEFAULT_RETRY_AFTER_SECONDS;
            }
        }
    }

    private int budgetRetryAfterSeconds() {
        long now = System.currentTimeMillis();
        synchronized (requestBudgetLock) {
            pruneRequestBudgetWindow(now);
            Long oldestRequest = requestBudgetWindow.peekFirst();
            if (oldestRequest == null) {
                return DEFAULT_RETRY_AFTER_SECONDS;
            }
            long millisUntilSlotOpens = Math.max(0, oldestRequest + REQUEST_BUDGET_WINDOW_MS - now);
            return boundedRetryAfter((int) Math.ceil(millisUntilSlotOpens / 1000.0));
        }
    }

    private int circuitOpenRetryAfterSeconds() {
        Long waitMillis = circuitBreaker.getCircuitBreakerConfig()
            .getWaitIntervalFunctionInOpenState()
            .apply(1);
        if (waitMillis == null) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        return boundedRetryAfter((int) Math.ceil(waitMillis / 1000.0));
    }

    private int boundedRetryAfter(int retryAfterSeconds) {
        return Math.max(1, Math.min(MAX_RETRY_AFTER_SECONDS, retryAfterSeconds));
    }

    private WebClientResponseException unwrapWebClientException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException exception) {
            return exception;
        }
        Throwable cause = throwable != null ? throwable.getCause() : null;
        if (cause instanceof WebClientResponseException exception) {
            return exception;
        }
        return null;
    }
    
    /**
     * Generic method to execute GET requests with common error handling and response processing
     * 
     * @param operationName Operation name for rate limiting and logging
     * @param uriSupplier Function to build the URI
     * @param responseType Class of the expected response type
     * @param successLogger Optional function to log successful responses (receives body and operation-specific data)
     * @return Response body or null if failed
     */
    private <T> T executeGetRequest(String operationName,
                                   Function<org.springframework.web.util.UriBuilder, URI> uriSupplier,
                                   Class<T> responseType,
                                   java.util.function.BiConsumer<T, String> successLogger) {
        String endpoint = sanitizedEndpoint(operationName, uriSupplier);
        return executeWithRateLimitCheck(operationName, endpoint, () -> {
            try {
                ResponseEntity<T> responseEntity = webClient.get()
                    .uri(uriSupplier)
                    .retrieve()
                    .toEntity(responseType)
                    .doOnSubscribe(subscription -> reserveRequestBudgetOrThrow(operationName))
                    .timeout(Duration.ofMillis(config.getApi().getTimeout().getRead()))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .retryWhen(createSimpleRetry())
                    .block();

                T body = responseEntity != null ? responseEntity.getBody() : null;
                if (body != null && successLogger != null) {
                    successLogger.accept(body, operationName);
                }
                HttpHeaders headers = responseEntity != null ? responseEntity.getHeaders() : null;
                Integer status = responseEntity != null ? responseEntity.getStatusCode().value() : null;
                return new ApiCallResult<>(body, headers, true, "success", status, null);
            } catch (WebClientResponseException e) {
                return handleApiError(operationName, e);
            } catch (Exception e) {
                return handleWrappedApiError(operationName, e);
            }
        });
    }

    /**
     * Generic method for GET requests with ParameterizedTypeReference (for generic types like List<T>)
     * 
     * @param operationName Operation name for rate limiting and logging
     * @param uriSupplier Function to build the URI
     * @param responseType ParameterizedTypeReference for generic response types
     * @param successLogger Optional function to log successful responses
     * @return Response body or null if failed
     */
    private <T> T executeGetRequest(String operationName,
                                   Function<org.springframework.web.util.UriBuilder, URI> uriSupplier,
                                   org.springframework.core.ParameterizedTypeReference<T> responseType,
                                   java.util.function.BiConsumer<T, String> successLogger) {
        String endpoint = sanitizedEndpoint(operationName, uriSupplier);
        return executeWithRateLimitCheck(operationName, endpoint, () -> {
            try {
                ResponseEntity<T> responseEntity = webClient.get()
                    .uri(uriSupplier)
                    .retrieve()
                    .toEntity(responseType)
                    .doOnSubscribe(subscription -> reserveRequestBudgetOrThrow(operationName))
                    .timeout(Duration.ofMillis(config.getApi().getTimeout().getRead()))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .retryWhen(createSimpleRetry())
                    .block();

                T body = responseEntity != null ? responseEntity.getBody() : null;
                if (body != null && successLogger != null) {
                    successLogger.accept(body, operationName);
                }
                HttpHeaders headers = responseEntity != null ? responseEntity.getHeaders() : null;
                Integer status = responseEntity != null ? responseEntity.getStatusCode().value() : null;
                return new ApiCallResult<>(body, headers, true, "success", status, null);
            } catch (WebClientResponseException e) {
                return handleApiError(operationName, e);
            } catch (Exception e) {
                return handleWrappedApiError(operationName, e);
            }
        });
    }

    private String sanitizedEndpoint(
        String operationName,
        Function<org.springframework.web.util.UriBuilder, URI> uriSupplier
    ) {
        try {
            URI uri = uriSupplier.apply(org.springframework.web.util.UriComponentsBuilder.newInstance());
            String path = uri != null ? uri.getRawPath() : null;
            if (path != null && !path.isBlank()) {
                return path;
            }
        } catch (RuntimeException e) {
            log.debug("Unable to derive market data reporting endpoint for {}", operationName);
        }
        return "/" + operationName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
    
    /**
     * Fetch flow alerts from upstream market data API
     * 
     * @param params Query parameters for filtering
     * @return UnusualWhalesFlowResponse containing flow alerts
     */
    @Cacheable(value = "apiResponses", key = "#params.toString()", unless = "#result == null")
    public UnusualWhalesFlowResponse getFlowAlerts(Map<String, Object> params) {
        return fetchFlowAlerts(params);
    }

    public UnusualWhalesFlowResponse getFlowAlertsUncached(Map<String, Object> params) {
        return fetchFlowAlerts(params);
    }

    private UnusualWhalesFlowResponse fetchFlowAlerts(Map<String, Object> params) {
        return executeGetRequest(FLOW_ALERTS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(FLOW_ALERTS_ENDPOINT);
                                    params.forEach((key, value) -> {
                                        if (value != null) {
                                            addIfPresent(builder, key, value);
                                        }
                                    });
                                    return builder.build();
                                },
                                UnusualWhalesFlowResponse.class,
                                 (body, op) -> {
                                     if (body.data() != null) {
                                         log.trace("Fetched {} flow alerts", body.data().size());
                                     }
                                });
    }
    
    /**
     * Fetch flow alerts with default parameters (recent alerts, limited batch)
     */
    public UnusualWhalesFlowResponse getRecentFlowAlerts() {
        return proxy().getFlowAlerts(Map.of(
            "min_dte", 1,
            "max_dte", 365,
            "limit", config.getFlow().getSync().getBatchSize()
        ));
    }

    public UnusualWhalesFlowResponse getLockedFlowAlerts() {
        return getFlowAlerts(lockedFlowAlertParams());
    }

    public UnusualWhalesFlowResponse getLockedFlowAlertsBetween(String newerThan, String olderThan) {
        Map<String, Object> params = lockedFlowAlertParams();
        if (newerThan != null && !newerThan.isBlank()) {
            params.put("newer_than", newerThan);
        }
        if (olderThan != null && !olderThan.isBlank()) {
            params.put("older_than", olderThan);
        }
        return getFlowAlerts(params);
    }

    private Map<String, Object> lockedFlowAlertParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("min_premium", 50_000);
        params.put("min_size", 100);
        params.put("min_volume", 100);
        params.put("all_opening", true);
        params.put("is_multi_leg", false);
        params.put("size_greater_oi", true);
        params.put("vol_greater_oi", true);
        params.put("limit", 200);
        return params;
    }
    
    /**
     * Fetch market tide data from upstream market data API
     * 
     * @param interval5m Use 5-minute intervals (default false for 1-minute)
     * @param otmOnly Filter for OTM options only
     * @param date Optional date in YYYY-MM-DD format
     * @return UnusualWhalesMarketTideDto containing market tide data
     */
    @Cacheable(value = "marketTideCache", cacheManager = "marketTideCacheManager", key = "(#interval5m ?: false) + '-' + (#otmOnly ?: false) + '-' + (#date ?: 'today')", unless = "#result == null")
    public UnusualWhalesMarketTideDto getMarketTide(Boolean interval5m, Boolean otmOnly, String date) {
        return getMarketTideUncached(interval5m, otmOnly, date);
    }

    public UnusualWhalesMarketTideDto getMarketTideUncached(Boolean interval5m, Boolean otmOnly, String date) {
        log.debug("[REST] getMarketTide called - interval5m={}, otmOnly={}, date={}", interval5m, otmOnly, date);
        log.trace("Fetching market tide data: interval5m={}, otmOnly={}, date={}", interval5m, otmOnly, date);
        
        return executeGetRequest(MARKET_TIDE_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/market/market-tide")
                                        .queryParam("interval_5m", Boolean.TRUE.equals(interval5m))
                                        .queryParam("otm_only", otmOnly == null || otmOnly);
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                UnusualWhalesMarketTideDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} market tide points", body.data().size());
                                    }
                                });
    }

    /**
     * Fetch market tide data from upstream market data API with default 1-minute intervals
     * 
     * @param otmOnly Filter for OTM options only
     * @return UnusualWhalesMarketTideDto containing market tide data
     */
    public UnusualWhalesMarketTideDto getMarketTide(Boolean otmOnly) {
        return proxy().getMarketTide(false, otmOnly, null);
    }

    /**
     * Fetch top net impact tickers (bullish and bearish) from upstream market data API
     * Returns tickers sorted by net premium with default parameters
     * 
     * Uncached: only invoked by the per-minute TopNetImpactSnapshot collector,
     * which needs fresh data each cycle; user requests are served from the
     * top_net_impact_snapshots table instead.
     * 
     * @return UnusualWhalesTopNetImpactDto containing top tickers by net premium
     */
    public UnusualWhalesTopNetImpactDto getTopNetImpact() {
        log.trace("Fetching top net impact data");
        return executeGetRequest(TOP_NET_IMPACT_OP,
                                uriBuilder -> uriBuilder.path("/api/market/top-net-impact").build(),
                                UnusualWhalesTopNetImpactDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} top net impact tickers", body.data().size());
                                    }
                                });
    }

    /**
     * Fetch SPY OHLC (Open, High, Low, Close) data from upstream market data API
     * 
     * @param date Optional date in YYYY-MM-DD format
     * @param interval Time interval (e.g., "1m" for 1 minute)
     * @return UnusualWhalesSpyOhlcDto containing SPY OHLC data
     */
    @Cacheable(value = "apiResponses", key = "'spy-ohlc-' + #interval + '-' + #date", unless = "#result == null")
    public UnusualWhalesSpyOhlcDto getSpyOhlc(String date, String interval) {
        log.debug("Fetching SPY OHLC data: date={}, interval={}", date, interval);
        return executeGetRequest(SPY_OHLC_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + "SPY/ohlc/" + interval);
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                UnusualWhalesSpyOhlcDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} SPY OHLC points", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch ticker-specific tide data (net premium ticks) from upstream market data API
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format
     * @return UnusualWhalesTickerTideDto containing ticker tide data
     */
    @Cacheable(value = "apiResponses", key = "'ticker-tide-' + #ticker + '-' + #date", unless = "#result == null")
    public UnusualWhalesTickerTideDto getTickerTide(String ticker, String date) {
        log.trace("Fetching ticker tide data for {}: date={}", ticker, date);
        return executeGetRequest(TICKER_TIDE_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/net-prem-ticks");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                UnusualWhalesTickerTideDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} tide points [{}]", body.data().size(), ticker);
                                    }
                                });
    }

    /**
     * Fetch max pain data by expiry for a specific ticker.
     *
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format
     * @return StockMaxPainResponse containing max pain rows for recent expiries
     */
    @Cacheable(value = "apiResponses", key = "'stock-max-pain-' + #ticker + '-' + (#date ?: 'today')", unless = "#result == null")
    public StockMaxPainResponse getStockMaxPain(String ticker, String date) {
        log.trace("Fetching stock max pain for {}: date={}", ticker, date);
        return executeGetRequest(STOCK_MAX_PAIN_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/max-pain");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                StockMaxPainResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} max pain rows [{}]", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch ticker OHLC (Open, High, Low, Close) data from upstream market data API
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format
     * @param interval Time interval (e.g., "1m" for 1 minute)
     * @return UnusualWhalesTickerOhlcDto containing ticker OHLC data
     */
    @Cacheable(value = "apiResponses", key = "'ticker-ohlc-' + #ticker + '-' + #interval + '-' + #date", unless = "#result == null")
    public UnusualWhalesTickerOhlcDto getTickerOhlc(String ticker, String date, String interval) {
        log.trace("Fetching {} OHLC data: date={}, interval={}", ticker, date, interval);
        return executeGetRequest(TICKER_OHLC_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/ohlc/" + interval);
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                UnusualWhalesTickerOhlcDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} OHLC points [{}]", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch current stock state (price, volume, prev close) from upstream market data API
     * 
     * @param ticker Stock ticker symbol
     * @return StockStateResponse containing current stock state data
     */
    @Cacheable(value = "apiResponses", key = "'stock-state-' + #ticker", unless = "#result == null")
    public StockStateResponse getStockState(String ticker) {
        log.trace("Fetching stock state for {}", ticker);
        return fetchStockState(ticker);
    }

    /**
     * Fetch current stock state without the shared apiResponses cache.
     * Used by the Strata stock-price collector where 30-second freshness matters.
     *
     * @param ticker Stock ticker symbol
     * @return StockStateResponse containing current stock state data
     */
    public StockStateResponse getStockStateForStrataCollector(String ticker) {
        log.trace("[STRATA-STOCK-PRICE] Fetching uncached stock state for {}", ticker);
        return fetchStockState(ticker);
    }

    /**
     * Fetch current stock state with a short-lived dedicated cache for scheduler market-hours checks.
     */
    @Cacheable(value = "marketStatus", key = "'stock-state-' + #ticker", cacheManager = "marketStatusCacheManager", unless = "#result == null")
    public StockStateResponse getStockStateForScheduler(String ticker) {
        log.trace("[MARKET-HOURS] Fetching scheduler stock state for {}", ticker);
        return fetchStockState(ticker);
    }

    private StockStateResponse fetchStockState(String ticker) {
        return executeGetRequest(STOCK_STATE_OP,
                                uriBuilder -> uriBuilder.path(STOCK_API_PATH + ticker + "/stock-state").build(),
                                StockStateResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched stock state [{}]: ${}", ticker, body.data().close());
                                    }
                                });
    }
    
    /**
     * Fetch stock volume price levels (dark & lit pool data) for a specific ticker
     * Returns aggregated volume by price level for both off-lit (dark pool) and lit (exchange) trades
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return StockVolumePriceLevelsResponse containing volume per price level
     */
    @Cacheable(value = "apiResponses", key = "'stock-volume-price-levels-' + #ticker + '-' + #date", unless = "#result == null")
    public StockVolumePriceLevelsResponse getStockVolumePriceLevels(String ticker, String date) {
        log.trace("Fetching stock volume price levels for {}: date={}", ticker, date);
        return executeGetRequest(STOCK_VOLUME_PRICE_LEVELS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/stock-volume-price-levels");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                StockVolumePriceLevelsResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} volume levels [{}]", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch all politicians (names and IDs)
     * Endpoint: GET /api/politician-portfolios/people
     * 
     * @return PoliticiansListResponse containing all politicians
     */
    @Cacheable(value = "apiResponses", key = "'politicians-list'", unless = "#result == null")
    public PoliticiansListResponse getPoliticiansList() {
        log.trace("Fetching politicians list");
        return executeGetRequest(POLITICIANS_LIST_OP,
                                uriBuilder -> uriBuilder.path("/api/politician-portfolios/people").build(),
                                PoliticiansListResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} politicians", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch recent politician trades (Senate/House stock transactions)
     * Endpoint: GET /api/politician-portfolios/recent_trades
     * 
     * @param limit Max trades to return (default 500, max 500)
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param ticker Filter by ticker symbol
     * @param politicianId Filter by politician UUID
     * @param filterLateReports Only show trades reported >45 days after transaction
     * @param page Page number for pagination (default 1)
     * @return PoliticianTradesResponse containing trades
     */
    @Cacheable(value = "apiResponses", 
               key = "'politician-trades-' + #limit + '-' + #date + '-' + #ticker + '-' + #politicianId + '-' + #filterLateReports + '-' + #page",
               unless = "#result == null")
    public PoliticianTradesResponse getPoliticianTrades(
            Integer limit, 
            String date, 
            String ticker, 
            UUID politicianId, 
            Boolean filterLateReports, 
            Integer page) {
        log.trace("Fetching politician trades: limit={}, date={}, ticker={}, politicianId={}, filterLateReports={}, page={}", 
                  limit, date, ticker, politicianId, filterLateReports, page);
        
        return executeGetRequest(POLITICIAN_TRADES_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/politician-portfolios/recent_trades");
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "ticker", ticker);
                                    addIfPresent(builder, "politician_id", politicianId);
                                    addIfPresent(builder, "filter_late_reports", filterLateReports);
                                    addIfPresent(builder, "page", page);
                                    return builder.build();
                                },
                                PoliticianTradesResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} politician trades", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch politician's portfolios and holdings
     * Endpoint: GET /api/politician-portfolios/{politician_id}
     * 
     * @param politicianId UUID of the politician
     * @param aggregateAllPortfolios Combine all portfolios into one "aggregated" portfolio
     * @return PoliticianPortfoliosResponse containing portfolios with holdings
     */
    @Cacheable(value = "apiResponses", 
               key = "'politician-portfolio-' + #politicianId + '-' + #aggregateAllPortfolios",
               unless = "#result == null")
    public PoliticianPortfoliosResponse getPoliticianPortfolios(UUID politicianId, Boolean aggregateAllPortfolios) {
        log.trace("Fetching politician portfolios for {}: aggregate={}", politicianId, aggregateAllPortfolios);
        return executeGetRequest(POLITICIAN_PORTFOLIOS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/politician-portfolios/" + politicianId);
                                    addIfPresent(builder, "aggregate_all_portfolios", aggregateAllPortfolios);
                                    return builder.build();
                                },
                                PoliticianPortfoliosResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} portfolios for politician {}", body.data().size(), politicianId);
                                    }
                                });
    }
    
    /**
     * Fetch recent congress trades
     * Endpoint: GET /api/congress/recent-trades
     * 
     * @return CongressTradesResponse containing trades from Congress API
     */
    @Cacheable(value = "apiResponses", 
               key = "'congress-trades'",
               unless = "#result == null")
    public CongressTradesResponse getCongressTrades() {
        log.trace("Fetching congress trades");
        return executeGetRequest(CONGRESS_TRADES_OP,
                                uriBuilder -> uriBuilder.path("/api/congress/recent-trades").build(),
                                CongressTradesResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} congress trades", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch recent congress trades with optional filters
     * Endpoint: GET /api/congress/recent-trades
     * 
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param limit Max trades to return (max: 200)
     * @param ticker Filter by ticker symbol
     * @return CongressTradesResponse containing filtered trades from Congress API
     */
    @Cacheable(value = "apiResponses", 
               key = "'congress-recent-trades-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#ticker ?: 'null')",
               unless = "#result == null")
    public CongressTradesResponse getCongressRecentTrades(String date, Integer limit, String ticker) {
        log.trace("Fetching congress recent trades: date={}, limit={}, ticker={}", date, limit, ticker);
        
        // Validate and cap limit at 200 (API restriction)
        Integer validatedLimit = limit;
        if (limit != null && limit > 200) {
            log.warn("Limit {} exceeds API maximum of 200, capping at 200", limit);
            validatedLimit = 200;
        }
        
        Integer finalLimit = validatedLimit;
        return executeGetRequest(CONGRESS_TRADES_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/congress/recent-trades");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", finalLimit);
                                    addIfPresent(builder, "ticker", ticker != null ? ticker.toUpperCase() : null);
                                    return builder.build();
                                },
                                CongressTradesResponse.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.trace("Fetched {} congress recent trades", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch greek exposure (gamma, delta, charm, vanna) aggregated by strike price
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data aggregated by strike
     */
    public GreekExposureResponseDto<GreekExposureByStrikeDto> getGreekExposureByStrike(String ticker, String date) {
        log.debug("Fetching greek exposure by strike for {} on {}", ticker, date != null ? date : "latest");
        
        return executeGetRequest(GREEK_EXPOSURE_BY_STRIKE_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/greek-exposure/strike");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                new org.springframework.core.ParameterizedTypeReference<GreekExposureResponseDto<GreekExposureByStrikeDto>>() {},
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} greek exposure by strike data points fetched for {}", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch greek exposure aggregated by expiration date
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data aggregated by expiry
     */
    public GreekExposureResponseDto<GreekExposureByExpiryDto> getGreekExposureByExpiry(String ticker, String date) {
        log.debug("Fetching greek exposure by expiry for {} on {}", ticker, date != null ? date : "latest");
        
        return executeGetRequest(GREEK_EXPOSURE_BY_EXPIRY_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/greek-exposure/expiry");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                new org.springframework.core.ParameterizedTypeReference<GreekExposureResponseDto<GreekExposureByExpiryDto>>() {},
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} greek exposure by expiry data points fetched for {}", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch greek exposure aggregated by strike and expiry date
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Expiration date (required, format: YYYY-MM-DD)
     * @param date Market date (optional, defaults to most recent trading day)
     * @return Greek exposure data aggregated by strike for specific expiry
     */
    public GreekExposureResponseDto<GreekExposureByStrikeExpiryDto> getGreekExposureByStrikeExpiry(
            String ticker, 
            String expiry, 
            String date) {
        log.debug("Fetching greek exposure by strike-expiry for {} - expiry: {}, date: {}", 
                  ticker, expiry, date != null ? date : "latest");
        
        return executeGetRequest(GREEK_EXPOSURE_BY_STRIKE_EXPIRY_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/greek-exposure/strike-expiry");
                                    builder.queryParam("expiry", expiry);
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                new org.springframework.core.ParameterizedTypeReference<GreekExposureResponseDto<GreekExposureByStrikeExpiryDto>>() {},
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} greek exposure by strike-expiry data points fetched for {} (expiry: {})", 
                                                 body.data().size(), ticker, expiry);
                                    }
                                });
    }
    
    /**
     * Fetch premarket earnings data from upstream market data API
     * 
     * @param date Date in YYYY-MM-DD format (optional)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return Detailed earnings response for premarket
     */
    @Cacheable(value = "apiResponses", key = "'earnings-premarket-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')", unless = "#result == null")
    public EarningsDetailedResponseDto getPremarketEarnings(String date, Integer limit, Integer page) {
        log.debug("Fetching premarket earnings for date: {}, limit: {}, page: {}", date, limit, page);
        return executeGetRequest(EARNINGS_PREMARKET_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/earnings/premarket");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    return builder.build();
                                },
                                EarningsDetailedResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} premarket earnings fetched for {}", body.data().size(), date != null ? date : "today");
                                    }
                                });
    }
    
    /**
     * Fetch afterhours earnings data from upstream market data API
     * 
     * @param date Date in YYYY-MM-DD format (optional)
     * @param limit Number of results (default: 50, max: 100, min: 1)
     * @param page Page number for pagination (optional)
     * @return Detailed earnings response for afterhours
     */
    @Cacheable(value = "apiResponses", key = "'earnings-afterhours-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')", unless = "#result == null")
    public EarningsDetailedResponseDto getAfterhoursEarnings(String date, Integer limit, Integer page) {
        log.debug("Fetching afterhours earnings for date: {}, limit: {}, page: {}", date, limit, page);
        return executeGetRequest(EARNINGS_AFTERHOURS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/earnings/afterhours");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    return builder.build();
                                },
                                EarningsDetailedResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} afterhours earnings fetched for {}", body.data().size(), date != null ? date : "today");
                                    }
                                });
    }
    
    /**
     * Fetch ticker-specific earnings data from upstream market data API
     * 
     * @param ticker Stock ticker symbol
     * @return Ticker earnings response containing earnings data
     */
    @Cacheable(value = "apiResponses", key = "'earnings-ticker-' + #ticker", unless = "#result == null")
    public TickerEarningsResponseDto getTickerEarnings(String ticker) {
        log.debug("Fetching ticker earnings for {}", ticker);
        
        return executeGetRequest(EARNINGS_TICKER_OP,
                                uriBuilder -> uriBuilder.path("/api/earnings/{ticker}").build(ticker.toUpperCase()),
                                TickerEarningsResponseDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} earnings data points fetched for {}", body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch OHLC (Open, High, Low, Close) candle data for a specific ticker
     * Endpoint: GET /api/stock/{ticker}/ohlc/{candleSize}
     * 
     * @param ticker Stock ticker symbol
     * @param candleSize Candle size (1m, 5m, 10m, 15m, 30m, 1h, 4h, 1d)
     * @param date Optional market date in YYYY-MM-DD format
     * @param timeframe Optional timeframe (1D, 2D, 1W, 2W, 1M, 2M, 1Y, 2Y, YTD)
     * @param endDate Optional end date in YYYY-MM-DD format (results go backwards from this date)
     * @param limit Optional limit for number of candles (max 2500, min 1)
     * @return OHLC data response containing candle data
     */
    @Cacheable(value = "apiResponses", 
               key = "'ohlc-' + #ticker + '-' + #candleSize + '-' + (#date ?: 'null') + '-' + (#timeframe ?: 'null') + '-' + (#endDate ?: 'null') + '-' + (#limit ?: 'null')",
               unless = "#result == null")
    public UnusualWhalesOhlcDto getOhlcData(
            String ticker,
            String candleSize,
            String date,
            String timeframe,
            String endDate,
            Integer limit) {
        
        log.debug("Fetching OHLC data for {} with candleSize={}, date={}, timeframe={}, endDate={}, limit={}", 
                  ticker, candleSize, date, timeframe, endDate, limit);
        
        return fetchOhlcData(ticker, candleSize, date, timeframe, endDate, limit);
    }

    /**
     * Fetch OHLC candle data without the shared apiResponses cache (5-minute TTL).
     * Used by the dashboard OHLC snapshot collector, which needs every ticker to
     * be current-minute fresh on each per-minute cycle; the shared cache would
     * leave tickers up to 5 minutes stale and out of sync with each other.
     */
    public UnusualWhalesOhlcDto getOhlcDataUncached(
            String ticker,
            String candleSize,
            String date,
            String timeframe,
            String endDate,
            Integer limit) {

        log.debug("[DASHBOARD-OHLC] Fetching uncached OHLC data for {} with candleSize={}, timeframe={}",
                  ticker, candleSize, timeframe);

        return fetchOhlcData(ticker, candleSize, date, timeframe, endDate, limit);
    }

    private UnusualWhalesOhlcDto fetchOhlcData(
            String ticker,
            String candleSize,
            String date,
            String timeframe,
            String endDate,
            Integer limit) {
        return executeGetRequest(OHLC_DATA_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/ohlc/" + candleSize);
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "timeframe", timeframe);
                                    addIfPresent(builder, "end_date", endDate);
                                    addIfPresent(builder, "limit", limit);
                                    return builder.build();
                                },
                                UnusualWhalesOhlcDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} OHLC candles fetched for {} with {} candle size", 
                                                 body.data().size(), ticker, candleSize);
                                    }
                                });
    }
    
    /**
     * Fetch recent dark pool trades from upstream market data API
     * Endpoint: GET /api/darkpool/recent
     * 
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param minPremium Minimum premium filter (default: 50000000)
     * @param maxPremium Maximum premium filter
     * @param minSize Minimum size filter
     * @param maxSize Maximum size filter
     * @param minVolume Minimum volume filter
     * @param maxVolume Maximum volume filter
     * @return DarkPoolRecentResponseDto containing recent dark pool trades
     */
    @Cacheable(value = "apiResponses", 
               key = "'darkpool-recent-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#minPremium ?: 'null') + '-' + (#maxPremium ?: 'null') + '-' + (#minSize ?: 'null') + '-' + (#maxSize ?: 'null') + '-' + (#minVolume ?: 'null') + '-' + (#maxVolume ?: 'null')",
               unless = "#result == null")
    public DarkPoolRecentResponseDto getDarkPoolRecent(
            String date,
            Integer limit,
            Long minPremium,
            Long maxPremium,
            Integer minSize,
            Integer maxSize,
            Integer minVolume,
            Integer maxVolume) {
        log.debug("Fetching recent dark pool trades - date: {}, limit: {}, minPremium: {}", date, limit, minPremium);
        
        return executeWithRateLimitCheck(DARKPOOL_RECENT_OP, "/api/darkpool/recent", () -> {
            try {
                ResponseEntity<DarkPoolRecentResponseDto> responseEntity = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/darkpool/recent");
                        if (date != null && !date.isBlank()) {
                            builder.queryParam("date", date);
                        }
                        if (limit != null) {
                            builder.queryParam("limit", limit);
                        }
                        if (minPremium != null) {
                            builder.queryParam("min_premium", minPremium);
                        }
                        if (maxPremium != null) {
                            builder.queryParam("max_premium", maxPremium);
                        }
                        if (minSize != null) {
                            builder.queryParam("min_size", minSize);
                        }
                        if (maxSize != null) {
                            builder.queryParam("max_size", maxSize);
                        }
                        if (minVolume != null) {
                            builder.queryParam("min_volume", minVolume);
                        }
                        if (maxVolume != null) {
                            builder.queryParam("max_volume", maxVolume);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .toEntity(DarkPoolRecentResponseDto.class)
                    .doOnSubscribe(subscription -> reserveRequestBudgetOrThrow(DARKPOOL_RECENT_OP))
                    .timeout(Duration.ofMillis(config.getApi().getTimeout().getRead()))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .retryWhen(createSimpleRetry())
                    .block();

                DarkPoolRecentResponseDto body = responseEntity != null ? responseEntity.getBody() : null;
                if (body != null && body.data() != null) {
                    log.debug("[REST] {} recent dark pool trades fetched", body.data().size());
                }
                HttpHeaders headers = responseEntity != null ? responseEntity.getHeaders() : null;
                Integer status = responseEntity != null ? responseEntity.getStatusCode().value() : null;
                return new ApiCallResult<>(body, headers, true, "success", status, null);
            } catch (WebClientResponseException e) {
                return handleApiError(DARKPOOL_RECENT_OP, e);
            } catch (Exception e) {
                return handleWrappedApiError(DARKPOOL_RECENT_OP, e);
            }
        });
    }
    
    /**
     * Fetch dark pool trades for a specific ticker
     * Endpoint: GET /api/darkpool/{ticker}
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format
     * @param limit Number of results (default: 200, max: 200, min: 1)
     * @param maxPremium Maximum premium filter
     * @param minPremium Minimum premium filter
     * @param newerThan Timestamp filter - return trades newer than this timestamp
     * @return Dark pool trades response for the ticker
     */
    @Cacheable(value = "apiResponses", 
               key = "'darkpool-ticker-' + #ticker + '-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#maxPremium ?: 'null') + '-' + (#minPremium ?: 'null') + '-' + (#newerThan ?: 'null')",
               unless = "#result == null")
    public DarkPoolRecentResponseDto getDarkPoolByTicker(
            String ticker,
            String date,
            Integer limit,
            Long maxPremium,
            Long minPremium,
            String newerThan) {
        log.debug("Fetching dark pool trades for ticker: {}, date: {}, limit: {}, maxPremium: {}, minPremium: {}, newerThan: {}", 
                  ticker, date, limit, maxPremium, minPremium, newerThan);
        
        return executeWithRateLimitCheck(DARKPOOL_TICKER_OP, "/api/darkpool/" + ticker.toUpperCase(), () -> {
            try {
                ResponseEntity<DarkPoolRecentResponseDto> responseEntity = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/darkpool/" + ticker.toUpperCase());
                        if (date != null && !date.isBlank()) {
                            builder.queryParam("date", date);
                        }
                        if (limit != null) {
                            builder.queryParam("limit", limit);
                        }
                        if (maxPremium != null) {
                            builder.queryParam("max_premium", maxPremium);
                        }
                        if (minPremium != null) {
                            builder.queryParam("min_premium", minPremium);
                        }
                        if (newerThan != null && !newerThan.isBlank()) {
                            builder.queryParam("newer_than", newerThan);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .toEntity(DarkPoolRecentResponseDto.class)
                    .doOnSubscribe(subscription -> reserveRequestBudgetOrThrow(DARKPOOL_TICKER_OP))
                    .timeout(Duration.ofMillis(config.getApi().getTimeout().getRead()))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .retryWhen(createSimpleRetry())
                    .block();

                DarkPoolRecentResponseDto body = responseEntity != null ? responseEntity.getBody() : null;
                if (body != null && body.data() != null) {
                    log.debug("[REST] {} dark pool trades fetched for ticker {}", body.data().size(), ticker);
                }
                HttpHeaders headers = responseEntity != null ? responseEntity.getHeaders() : null;
                Integer status = responseEntity != null ? responseEntity.getStatusCode().value() : null;
                return new ApiCallResult<>(body, headers, true, "success", status, null);
            } catch (WebClientResponseException e) {
                return handleApiError(DARKPOOL_TICKER_OP, e);
            } catch (Exception e) {
                return handleWrappedApiError(DARKPOOL_TICKER_OP, e);
            }
        });
    }
    
    /**
     * Fetch option contracts from screener API
     * Endpoint: GET /api/screener/option-contracts
     * 
     * @param params Query parameters for filtering
     * @return OptionContractsResponse containing option contracts data
     */
    @Cacheable(value = "apiResponses", 
               key = "'option-contracts-' + #params.toString()",
               unless = "#result == null")
    public OptionContractsResponse getOptionContracts(Map<String, Object> params) {
        return getOptionContractsUncached(params);
    }

    /**
     * Uncached variant used by the per-minute dashboard contract snapshot
     * collector, which needs fresh data each cycle.
     */
    public OptionContractsResponse getOptionContractsUncached(Map<String, Object> params) {
        log.debug("Fetching option contracts with params: {}", params);
        
        return executeWithRateLimitCheck(OPTION_CONTRACTS_OP, "/api/screener/option-contracts", () -> {
            try {
                ResponseEntity<OptionContractsResponse> responseEntity = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/screener/option-contracts");
                        params.forEach((key, value) -> {
                            if (value != null) {
                                addIfPresent(builder, key, value);
                            }
                        });
                        return builder.build();
                    })
                    .retrieve()
                    .toEntity(OptionContractsResponse.class)
                    .doOnSubscribe(subscription -> reserveRequestBudgetOrThrow(OPTION_CONTRACTS_OP))
                    .timeout(Duration.ofMillis(config.getApi().getTimeout().getRead()))
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .retryWhen(createSimpleRetry())
                    .block();

                OptionContractsResponse body = responseEntity != null ? responseEntity.getBody() : null;
                if (body != null && body.data() != null) {
                    log.debug("[REST] {} option contracts fetched", body.data().size());
                }
                HttpHeaders headers = responseEntity != null ? responseEntity.getHeaders() : null;
                Integer status = responseEntity != null ? responseEntity.getStatusCode().value() : null;
                return new ApiCallResult<>(body, headers, true, "success", status, null);
            } catch (WebClientResponseException e) {
                return handleApiError(OPTION_CONTRACTS_OP, e);
            } catch (Exception e) {
                return handleWrappedApiError(OPTION_CONTRACTS_OP, e);
            }
        });
    }

    /**
     * Fetch options volume data for a specific ticker
     * Endpoint: GET /api/stock/{ticker}/options-volume
     * 
     * Returns the options volume & premium for all trade executions
     * that happened on a given trading date for the given ticker.
     * 
     * @param ticker Stock ticker symbol
     * @param limit Number of results (default: 1, max: 500, min: 1)
     * @return OptionsVolumeDto containing options volume data
     */
    @Cacheable(value = "apiResponses", 
               key = "'options-volume-' + #ticker + '-' + (#limit ?: 'null')",
               unless = "#result == null")
    public OptionsVolumeDto getOptionsVolume(String ticker, Integer limit) {
        log.debug("Fetching options volume for {} with limit: {}", ticker, limit);
        
        return executeGetRequest(OPTIONS_VOLUME_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/options-volume");
                                    addIfPresent(builder, "limit", limit);
                                    return builder.build();
                                },
                                OptionsVolumeDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} options volume data points fetched for {}", body.data().size(), ticker);
                                    }
                                });
    }

    /**
     * Fetch spot exposure data aggregated by strike price
     * Endpoint: GET /api/stock/{ticker}/spot-exposures/strike
     * 
     * @param ticker Stock ticker symbol
     * @param date Market date (optional, defaults to most recent trading day)
     * @param limit Number of results (default: 50)
     * @param maxStrike Maximum strike price filter (optional)
     * @return Spot exposure data aggregated by strike
     */
    @Cacheable(value = "apiResponses", 
               key = "'spot-exposure-strike-' + #ticker + '-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#maxStrike ?: 'null')",
               unless = "#result == null")
    public SpotExposureResponseDto getSpotExposureByStrike(
            String ticker,
            String date,
            Integer limit,
            BigDecimal maxStrike) {
        log.debug("Fetching spot exposure by strike for {} - date: {}, limit: {}, maxStrike: {}", 
                  ticker, date, limit, maxStrike);
        
        return executeGetRequest(SPOT_EXPOSURE_BY_STRIKE_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/spot-exposures/strike");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", limit != null ? limit : 50);
                                    addIfPresent(builder, "max_strike", maxStrike);
                                    return builder.build();
                                },
                                SpotExposureResponseDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} spot exposure by strike data points fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }

    /**
     * Fetch spot exposure data aggregated by expiry and strike
     * Endpoint: GET /api/stock/{ticker}/spot-exposures/expiry-strike
     * 
     * @param ticker Stock ticker symbol
     * @param expirations Array of expiration dates (required)
     * @param date Market date (optional, defaults to most recent trading day)
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination (default: 0)
     * @param minStrike Minimum strike price filter (optional)
     * @param maxStrike Maximum strike price filter (optional)
     * @param minDte Minimum days to expiry filter (optional)
     * @param maxDte Maximum days to expiry filter (optional)
     * @return Spot exposure data aggregated by expiry and strike
     */
    @Cacheable(value = "apiResponses", 
               key = "'spot-exposure-expiry-strike-' + #ticker + '-' + #expirations.toString() + '-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null') + '-' + (#minStrike ?: 'null') + '-' + (#maxStrike ?: 'null') + '-' + (#minDte ?: 'null') + '-' + (#maxDte ?: 'null')",
               unless = "#result == null")
    public SpotExposureResponseDto getSpotExposureByExpiryStrike(
            String ticker,
            List<String> expirations,
            String date,
            Integer limit,
            Integer page,
            Integer minStrike,
            Integer maxStrike,
            Integer minDte,
            Integer maxDte) {
        log.debug("Fetching spot exposure by expiry-strike for {} - expirations: {}, date: {}, limit: {}",
                  ticker, expirations, date, limit);

        return fetchSpotExposureByExpiryStrike(
            ticker,
            expirations,
            date,
            limit,
            page,
            minStrike,
            maxStrike,
            minDte,
            maxDte
        );
    }

    public SpotExposureResponseDto getSpotExposureByExpiryStrikeUncached(
            String ticker,
            List<String> expirations,
            String date,
            Integer limit,
            Integer page,
            Integer minStrike,
            Integer maxStrike,
            Integer minDte,
            Integer maxDte) {
        log.debug("[SPOT-GEX] Fetching uncached spot exposure by expiry-strike for {} - expirations: {}, date: {}, limit: {}, page: {}",
                  ticker, expirations, date, limit, page);

        return fetchSpotExposureByExpiryStrike(
            ticker,
            expirations,
            date,
            limit,
            page,
            minStrike,
            maxStrike,
            minDte,
            maxDte
        );
    }

    private SpotExposureResponseDto fetchSpotExposureByExpiryStrike(
            String ticker,
            List<String> expirations,
            String date,
            Integer limit,
            Integer page,
            Integer minStrike,
            Integer maxStrike,
            Integer minDte,
            Integer maxDte) {
        return executeGetRequest(SPOT_EXPOSURE_BY_EXPIRY_STRIKE_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/spot-exposures/expiry-strike");
                                    // Add expirations as array parameter
                                    if (expirations != null && !expirations.isEmpty()) {
                                        expirations.forEach(exp -> builder.queryParam("expirations[]", exp));
                                    }
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    addIfPresent(builder, "min_strike", minStrike);
                                    addIfPresent(builder, "max_strike", maxStrike);
                                    addIfPresent(builder, "min_dte", minDte);
                                    addIfPresent(builder, "max_dte", maxDte);
                                    return builder.build();
                                },
                                SpotExposureResponseDto.class,
                                (body, op) -> {
                                    if (body.data() != null) {
                                        log.debug("[REST] {} spot exposure by expiry-strike data points fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }

    private void addIfPresent(org.springframework.web.util.UriBuilder builder, String key, Object value) {
        if (value != null) {
            if (value instanceof String s) {
                if (s.isBlank()) {
                    return;
                }
                builder.queryParam(key, s);
                return;
            }

            if (value instanceof Iterable<?> iterable) {
                iterable.forEach(item -> addIfPresent(builder, key, item));
                return;
            }

            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    addIfPresent(builder, key, Array.get(value, i));
                }
                return;
            }

            builder.queryParam(key, value);
        }
    }
    
    /**
     * Fetch insider transactions from upstream market data API
     * Endpoint: GET /api/insider/transactions
     * 
     * @param tickerSymbol Optional ticker symbol to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @return Insider transactions response
     */
    @Cacheable(value = "apiResponses", 
               key = "'insider-transactions-' + (#tickerSymbol ?: 'all') + '-' + (#limit ?: 'null')",
               unless = "#result == null")
    public InsiderTransactionsResponseDto getInsiderTransactions(String tickerSymbol, Integer limit) {
        log.debug("Fetching insider transactions - ticker: {}, limit: {}", tickerSymbol, limit);
        
        return executeGetRequest(INSIDER_TRANSACTIONS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/insider/transactions");
                                    if (tickerSymbol != null && !tickerSymbol.isBlank()) {
                                        builder.queryParam("ticker_symbol", tickerSymbol.toUpperCase());
                                    }
                                    if (limit != null) {
                                        builder.queryParam("limit", limit);
                                    }
                                    return builder.build();
                                },
                                InsiderTransactionsResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} insider transactions fetched", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch analyst ratings from upstream market data API
     * Endpoint: GET /api/screener/analysts
     * 
     * @param ticker Optional ticker symbol to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @return Analyst ratings response
     */
    @Cacheable(value = "apiResponses", 
               key = "'analyst-ratings-' + (#ticker ?: 'all') + '-' + (#limit ?: 'null')",
               unless = "#result == null")
    public AnalystRatingsResponseDto getAnalystRatings(String ticker, Integer limit) {
        log.debug("Fetching analyst ratings - ticker: {}, limit: {}", ticker, limit);
        
        return executeGetRequest(ANALYST_RATINGS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/screener/analysts");
                                    addIfPresent(builder, "ticker", ticker != null ? ticker.toUpperCase() : null);
                                    addIfPresent(builder, "limit", limit);
                                    return builder.build();
                                },
                                AnalystRatingsResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} analyst ratings fetched", body.data().size());
                                    }
                                });
    }
    
    /**
     * Fetch option chains (all option symbols) for a ticker from upstream market data API
     * Endpoint: GET /api/stock/{ticker}/option-chains
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional market date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Option chains response containing list of option symbols
     */
    @Cacheable(value = "apiResponses", 
               key = "'option-chains-' + #ticker + '-' + (#date ?: 'null')",
               unless = "#result == null")
    public OptionChainsResponseDto getOptionChains(String ticker, String date) {
        log.debug("Fetching option chains - ticker: {}, date: {}", ticker, date);

        return fetchOptionChains(ticker, date);
    }

    public OptionChainsResponseDto getOptionChainsUncached(String ticker, String date) {
        log.debug("[SPOT-GEX] Fetching uncached option chains - ticker: {}, date: {}", ticker, date);

        return fetchOptionChains(ticker, date);
    }

    private OptionChainsResponseDto fetchOptionChains(String ticker, String date) {
        return executeGetRequest(OPTION_CHAINS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/option-chains");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                OptionChainsResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} option symbols fetched for {}", body.data().size(), ticker);
                                    }
                                 });
    }

    /**
     * Fetch option expiry breakdown for a ticker.
     * Endpoint: GET /api/stock/{ticker}/expiry-breakdown
     *
     * @param ticker Stock ticker symbol
     * @return Expiry breakdown rows containing provider-valid expiry dates
     */
    @Cacheable(value = "apiResponses",
               key = "'expiry-breakdown-' + #ticker",
               unless = "#result == null")
    public ExpiryBreakdownResponseDto getExpiryBreakdown(String ticker) {
        log.debug("Fetching expiry breakdown - ticker: {}", ticker);

        return fetchExpiryBreakdown(ticker);
    }

    public ExpiryBreakdownResponseDto getExpiryBreakdownUncached(String ticker) {
        log.debug("[SPOT-GEX] Fetching uncached expiry breakdown - ticker: {}", ticker);

        return fetchExpiryBreakdown(ticker);
    }

    private ExpiryBreakdownResponseDto fetchExpiryBreakdown(String ticker) {
        return executeGetRequest(EXPIRY_BREAKDOWN_OP,
                                uriBuilder -> uriBuilder
                                    .path(STOCK_API_PATH + ticker + "/expiry-breakdown")
                                    .build(),
                                ExpiryBreakdownResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} expiry breakdown rows fetched for {}",
                                            body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch price change per month data (seasonality) for a ticker from upstream market data API
     * Endpoint: GET /api/seasonality/{ticker}/year-month
     * 
     * Returns the relative price change for all past months over multiple years.
     * 
     * @param ticker Stock ticker symbol
     * @return Price change per month response containing historical monthly data
     */
    @Cacheable(value = "apiResponses", 
               key = "'price-change-per-month-' + #ticker",
               unless = "#result == null")
    public PriceChangePerMonthResponseDto getPriceChangePerMonth(String ticker) {
        log.debug("Fetching price change per month for ticker: {}", ticker);
        
        return executeGetRequest(PRICE_CHANGE_PER_MONTH_OP,
                                uriBuilder -> uriBuilder.path("/api/seasonality/" + ticker + "/year-month").build(),
                                PriceChangePerMonthResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} price change per month data points fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch stock information (company details, market cap, sector, etc.) from upstream market data API
     * Endpoint: GET /api/stock/{ticker}/info
     * 
     * Returns detailed information about a stock including company name, sector,
     * market cap, options availability, earnings dates, and more.
     * 
     * @param ticker Stock ticker symbol
     * @return Stock information response containing company and stock details
     */
    @Cacheable(value = "apiResponses", 
               key = "'stock-information-' + #ticker",
               unless = "#result == null")
    public StockInformationResponse getStockInformation(String ticker) {
        log.debug("Fetching stock information for ticker: {}", ticker);
        
        return executeGetRequest(STOCK_INFORMATION_OP,
                                uriBuilder -> uriBuilder.path(STOCK_API_PATH + ticker + "/info").build(),
                                StockInformationResponse.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] Stock information fetched for {}: {} ({})", 
                                                 ticker, body.data().fullName(), body.data().sector());
                                    }
                                });
    }
    
    /**
     * Fetch historic data for an option contract from upstream market data API
     * Endpoint: GET /api/option-contract/{id}/historic
     * 
     * Returns historic data for every trading day of the given option contract.
     * The contract ID must be in ISO format (e.g., TSLA230526P00167500).
     * 
     * @param contractId Option contract in ISO format (e.g., TSLA230526P00167500)
     * @param limit Optional limit for number of results
     * @return Historic option contract data containing daily chains
     */
    @Cacheable(value = "apiResponses", 
               key = "'option-contract-historic-' + #contractId + '-' + (#limit ?: 'null')",
               unless = "#result == null")
    public OptionContractHistoricResponseDto getOptionContractHistoric(String contractId, Integer limit) {
        log.debug("Fetching historic data for option contract: {}, limit: {}", contractId, limit);
        
        return executeGetRequest(OPTION_CONTRACT_HISTORIC_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/option-contract/" + contractId + "/historic");
                                    addIfPresent(builder, "limit", limit);
                                    return builder.build();
                                },
                                OptionContractHistoricResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.chains() != null) {
                                        log.debug("[REST] {} historic data points fetched for option contract {}", 
                                                 body.chains().size(), contractId);
                                    }
                                });
    }
    
    /**
     * Fetch intraday data for an option contract from upstream market data API
     * Endpoint: GET /api/option-contract/{id}/intraday
     * 
     * Returns 1-minute interval intraday data for the given option contract.
     * The contract ID must be in ISO format (e.g., GOOGL260220P00310000).
     * Date must be the current or a past date. If no date is given, returns data for the current/last market day.
     * 
     * @param contractId Option contract in ISO format (e.g., GOOGL260220P00310000)
     * @param date Optional date in YYYY-MM-DD format (defaults to current/last market day)
     * @return Intraday option contract data containing 1-minute ticks
     */
    @Cacheable(value = "apiResponses", 
               key = "'option-contract-intraday-' + #contractId + '-' + (#date ?: 'null')",
               unless = "#result == null")
    public OptionContractIntradayResponseDto getOptionContractIntraday(String contractId, String date) {
        log.debug("Fetching intraday data for option contract: {}, date: {}", contractId, date);
        
        return executeGetRequest(OPTION_CONTRACT_INTRADAY_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/option-contract/" + contractId + "/intraday");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                OptionContractIntradayResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} intraday data points fetched for option contract {}", 
                                                 body.data().size(), contractId);
                                    }
                                });
    }
    
    /**
     * Fetch all option contracts for a ticker from upstream market data API
     * Endpoint: GET /api/stock/{ticker}/option-contracts
     * 
     * Returns all option contracts for the given ticker with various filter options.
     * 
     * @param ticker Stock ticker symbol
     * @param expiry Single expiry date in ISO date format (YYYY-MM-DD)
     * @param optionType Option type to filter by ("call", "Call", "put", "Put")
     * @param volGreaterOi Only return chains where volume > open interest
     * @param excludeZeroVolChains Only return chains where volume > 0
     * @param excludeZeroDte Only return chains which do not expire on the same day
     * @param excludeZeroOiChains Only return chains where open interest > 0
     * @param maybeOtmOnly Only return chains which are out of the money
     * @param optionSymbols Options symbols to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination (starts on page 0)
     * @return All option contracts for the ticker matching filters
     */
    @Cacheable(value = "apiResponses", 
               key = "'option-contracts-all-' + #ticker + '-' + (#expiry ?: 'null') + '-' + (#optionType ?: 'null') + '-' + (#volGreaterOi ?: 'null') + '-' + (#excludeZeroVolChains ?: 'null') + '-' + (#excludeZeroDte ?: 'null') + '-' + (#excludeZeroOiChains ?: 'null') + '-' + (#maybeOtmOnly ?: 'null') + '-' + (#optionSymbols != null ? #optionSymbols.toString() : 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')",
               unless = "#result == null")
    public OptionContractsAllResponseDto getOptionContractsAll(
            String ticker,
            String expiry,
            String optionType,
            Boolean volGreaterOi,
            Boolean excludeZeroVolChains,
            Boolean excludeZeroDte,
            Boolean excludeZeroOiChains,
            Boolean maybeOtmOnly,
            List<String> optionSymbols,
            Integer limit,
            Integer page) {
        log.debug("Fetching all option contracts for ticker: {}, expiry: {}, optionType: {}, limit: {}", 
                  ticker, expiry, optionType, limit);
        
        return executeGetRequest(OPTION_CONTRACTS_ALL_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/option-contracts");
                                    addIfPresent(builder, "expiry", expiry);
                                    addIfPresent(builder, "option_type", optionType);
                                    addIfPresent(builder, "vol_greater_oi", volGreaterOi);
                                    addIfPresent(builder, "exclude_zero_vol_chains", excludeZeroVolChains);
                                    addIfPresent(builder, "exclude_zero_dte", excludeZeroDte);
                                    addIfPresent(builder, "exclude_zero_oi_chains", excludeZeroOiChains);
                                    addIfPresent(builder, "maybe_otm_only", maybeOtmOnly);
                                    if (optionSymbols != null && !optionSymbols.isEmpty()) {
                                        optionSymbols.forEach(symbol -> builder.queryParam("option_symbol", symbol));
                                    }
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    return builder.build();
                                },
                                OptionContractsAllResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} option contracts fetched for ticker {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch IV Rank data for a ticker from upstream market data API
     * Endpoint: GET /api/stock/{ticker}/iv-rank
     * 
     * Returns the IV rank data for a ticker over a period of time.
     * IV rank is a measure of where current implied volatility stands relative to its historical range.
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format (defaults to last trading date)
     * @param timespan Optional timespan parameter ('1y', '6m', '3m', '1m', '1w' or ISO date like '2024-01-25')
     * @return IV Rank response containing historical IV rank data
     */
    @Cacheable(value = "apiResponses", 
               key = "'iv-rank-' + #ticker + '-' + (#date ?: 'null') + '-' + (#timespan ?: 'null')",
               unless = "#result == null")
    public IvRankResponseDto getIvRank(String ticker, String date, String timespan) {
        log.debug("Fetching IV rank for ticker: {}, date: {}, timespan: {}", ticker, date, timespan);
        
        return executeGetRequest(IV_RANK_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/iv-rank");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "timespan", timespan);
                                    return builder.build();
                                },
                                IvRankResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} IV rank data points fetched for ticker {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch Realized Volatility data for a ticker from upstream market data API
     * Endpoint: GET /api/stock/{ticker}/volatility/realized
     * 
     * Returns implied volatility vs realized volatility comparison data.
     * Realized volatility is the actual historical volatility observed in the stock price.
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional trading date in YYYY-MM-DD format (defaults to last trading date)
     * @param timeframe Optional timeframe ('YTD', '1D', '2D', '1W', '2W', '1M', '2M', '1Y', '2Y', default: '1Y')
     * @return Realized volatility response containing IV vs RV comparison data
     */
    @Cacheable(value = "apiResponses", 
               key = "'realized-volatility-' + #ticker + '-' + (#date ?: 'null') + '-' + (#timeframe ?: 'null')",
               unless = "#result == null")
    public RealizedVolatilityResponseDto getRealizedVolatility(String ticker, String date, String timeframe) {
        log.debug("Fetching realized volatility for ticker: {}, date: {}, timeframe: {}", ticker, date, timeframe);
        
        return executeGetRequest(REALIZED_VOLATILITY_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/volatility/realized");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "timeframe", timeframe);
                                    return builder.build();
                                },
                                RealizedVolatilityResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} realized volatility data points fetched for ticker {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch options price levels with call and put volume
     * Endpoint: GET /api/stock/{ticker}/option/stock-price-levels
     * 
     * Returns volume distribution across different price levels for the ticker's options.
     * 
     * @param ticker Stock ticker symbol
     * @param date Optional market date in YYYY-MM-DD format (defaults to current/last trading day)
     * @return Options price levels response with call and put volume at each price
     */
    @Cacheable(value = "apiResponses", 
               key = "'options-price-levels-' + #ticker + '-' + (#date ?: 'null')",
               unless = "#result == null")
    public StockPriceLevelsResponseDto getStockPriceLevels(String ticker, String date) {
        log.debug("Fetching options price levels for ticker: {}, date: {}", ticker, date);
        
        return executeGetRequest(OPTIONS_PRICE_LEVELS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path(STOCK_API_PATH + ticker + "/option/stock-price-levels");
                                    addIfPresent(builder, "date", date);
                                    return builder.build();
                                },
                                StockPriceLevelsResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} options price levels fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch institutional ownership data for a ticker
     * Endpoint: GET /api/institution/{ticker}/ownership
     *
     * @param ticker Stock ticker symbol(s), comma-separated
     * @param date Report date in YYYY-MM-DD format (optional)
     * @param startDate Only holdings with report_date on or after this date (optional)
     * @param endDate Only holdings with report_date on or before this date (optional)
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @param page Page number for pagination (starts at 0)
     * @param order Column to sort by (optional)
     * @param orderDirection Sort direction: "asc" or "desc" (default: "desc")
     * @param tags Array of institution tags (optional, e.g. "activist")
     * @return Institutional ownership response
     */
    @Cacheable(value = "apiResponses",
               key = "'inst-ownership-' + #ticker + '-' + (#date ?: 'null') + '-' + (#startDate ?: 'null') + '-' + (#endDate ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null') + '-' + (#order ?: 'null') + '-' + (#orderDirection ?: 'null') + '-' + (#tags != null ? #tags.toString() : 'null')",
               unless = "#result == null")
    public InstitutionalOwnershipResponseDto getInstitutionalOwnership(
            String ticker,
            String date,
            String startDate,
            String endDate,
            Integer limit,
            Integer page,
            String order,
            String orderDirection,
            List<String> tags) {
        log.debug("Fetching institutional ownership for ticker: {}, date: {}, limit: {}, page: {}", 
                  ticker, date, limit, page);
        
        return executeGetRequest(INSTITUTIONAL_OWNERSHIP_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/institution/" + ticker.toUpperCase() + "/ownership");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "start_date", startDate);
                                    addIfPresent(builder, "end_date", endDate);
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    addIfPresent(builder, "order", order);
                                    addIfPresent(builder, "order_direction", orderDirection);
                                    if (tags != null && !tags.isEmpty()) {
                                        tags.forEach(tag -> builder.queryParam("tags[]", tag));
                                    }
                                    return builder.build();
                                },
                                InstitutionalOwnershipResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} institutional ownership records fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
    
    /**
     * Fetch ETF holdings for a given ticker
     * Endpoint: GET /api/etfs/{ticker}/holdings
     *
     * @param ticker ETF ticker symbol (e.g. SPY, QQQ)
     * @param date   Optional date in YYYY-MM-DD format
     * @param limit  Number of results (default: 500, max: 500, min: 1)
     * @param page   Page number for pagination (starts at 0)
     * @return ETF holdings response
     */
    @Cacheable(value = "apiResponses",
               key = "'etf-holdings-' + #ticker + '-' + (#date ?: 'null') + '-' + (#limit ?: 'null') + '-' + (#page ?: 'null')",
               unless = "#result == null")
    public EtfHoldingsResponseDto getEtfHoldings(String ticker, String date, Integer limit, Integer page) {
        log.debug("Fetching ETF holdings for ticker: {}, date: {}, limit: {}, page: {}", ticker, date, limit, page);
        
        return executeGetRequest(ETF_HOLDINGS_OP,
                                uriBuilder -> {
                                    var builder = uriBuilder.path("/api/etfs/" + ticker.toUpperCase() + "/holdings");
                                    addIfPresent(builder, "date", date);
                                    addIfPresent(builder, "limit", limit);
                                    addIfPresent(builder, "page", page);
                                    return builder.build();
                                },
                                EtfHoldingsResponseDto.class,
                                (body, op) -> {
                                    if (body != null && body.data() != null) {
                                        log.debug("[REST] {} ETF holdings fetched for {}", 
                                                 body.data().size(), ticker);
                                    }
                                });
    }
}
