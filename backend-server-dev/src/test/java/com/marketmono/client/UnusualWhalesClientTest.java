package com.marketmono.client;

import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.config.UnusualWhalesShed503Properties;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import com.marketmono.dto.StockStateResponse;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.metrics.UwCallSource;
import com.marketmono.metrics.UwCallSourceContext;
import com.marketmono.service.RateLimitService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UnusualWhalesClientTest {

    private static final String LOOPBACK_HOST = "127.0.0.1";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private AtomicInteger requestCount;
    private HttpServer adminServer;
    private ExecutorService adminServerExecutor;
    private AtomicInteger adminRequestCount;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/__ready", exchange -> writeJson(exchange, 200, "{}"));
        requestCount = new AtomicInteger();
        adminRequestCount = new AtomicInteger();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (adminServer != null) {
            adminServer.stop(0);
        }
        if (adminServerExecutor != null) {
            adminServerExecutor.shutdownNow();
        }
    }

    @Test
    void shouldReturnNullQuicklyWhenConcurrencyPermitIsUnavailableWithoutInflatingPermits() throws Exception {
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            firstRequestStarted.countDown();
            try {
                releaseFirstRequest.await(2, TimeUnit.SECONDS);
                writeJson(exchange, 200, stockStateJson());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, "{}");
            }
        });
        server.start();

        UnusualWhalesClient client = client(1, 25, 100, 0);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<StockStateResponse> firstCall = callerExecutor.submit(() -> client.getStockState("SPY"));

            assertThat(firstRequestStarted.await(1, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            StockStateResponse secondCall = client.getStockState("SPY");
            StockStateResponse thirdCall = client.getStockState("SPY");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(secondCall).isNull();
            assertThat(thirdCall).isNull();
            assertThat(elapsedMs).isLessThan(500);
            assertThat(requestCount).hasValue(1);

            releaseFirstRequest.countDown();
            assertThat(firstCall.get(2, TimeUnit.SECONDS)).isNotNull();
        } finally {
            releaseFirstRequest.countDown();
            callerExecutor.shutdownNow();
        }
    }

    @Test
    void shouldApplyRollingPerNodeRequestBudgetBeforeCallingUw() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 1, 0);

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(client.getStockState("SPY")).isNull();
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void shouldSurfaceBudgetShedWithCauseAwareRetryAfterWhenShed503Enabled() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 1, 0, null, true);

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThatThrownBy(() -> client.getStockState("SPY"))
            .isInstanceOf(UnusualWhalesShedException.class)
            .satisfies(throwable -> {
                UnusualWhalesShedException exception = (UnusualWhalesShedException) throwable;
                assertThat(exception.operationName()).isEqualTo("stock state");
                assertThat(exception.reason()).isEqualTo("budget");
                assertThat(exception.retryAfterSeconds()).isBetween(55, 65);
            });
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void shouldParseHttpDateRetryAfterWhenUpstreamReturns429() {
        String retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30)
        );
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add(HttpHeaders.RETRY_AFTER, retryAfter);
            writeJson(exchange, 429, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, true);

        assertThatThrownBy(() -> client.getStockState("SPY"))
            .isInstanceOf(UnusualWhalesShedException.class)
            .satisfies(throwable -> {
                UnusualWhalesShedException exception = (UnusualWhalesShedException) throwable;
                assertThat(exception.reason()).isEqualTo("upstream_429");
                assertThat(exception.retryAfterSeconds()).isBetween(20, 35);
            });
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void shouldUseCircuitOpenWaitDurationForCircuitOpenShedRetryAfter() {
        server.start();
        RateLimitService rateLimitService = mock(RateLimitService.class);
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
            "unusual-whales-open-test",
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(2)
                .minimumNumberOfCalls(1)
                .build()
        );
        circuitBreaker.transitionToOpenState();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, true, rateLimitService, circuitBreaker);

        assertThatThrownBy(() -> client.getStockState("SPY"))
            .isInstanceOf(UnusualWhalesShedException.class)
            .satisfies(throwable -> {
                UnusualWhalesShedException exception = (UnusualWhalesShedException) throwable;
                assertThat(exception.reason()).isEqualTo("circuit_open");
                assertThat(exception.retryAfterSeconds()).isEqualTo(30);
            });
        assertThat(requestCount).hasValue(0);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void shouldPublishUwUsageMetricsForSuccessfulAndBudgetRejectedCalls() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        server.start();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UnusualWhalesClient client = client(2, 100, 1, 0, meterRegistry);

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(client.getStockState("SPY")).isNull();

        assertThat(counter(meterRegistry, "marketmono.market_data.requests.reserved", "operation", "stock state"))
            .isEqualTo(1.0);
        assertThat(counter(
            meterRegistry,
            "marketmono.market_data.requests.completed",
            "operation",
            "stock state",
            "result",
            "success"
        )).isEqualTo(1.0);
        assertThat(counter(
            meterRegistry,
            "marketmono.market_data.requests.rejected",
            "operation",
            "stock state",
            "reason",
            "budget"
        )).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.rolling.minute.usage")).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.target.per.minute")).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.hard.cap.per.minute")).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.available.permits")).isEqualTo(2.0);
    }

    @Test
    void shouldTagUwUsageMetricsWithCurrentBoundedSource() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        server.start();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UnusualWhalesClient client = client(2, 100, 10, 0, meterRegistry);

        StockStateResponse response = UwCallSourceContext.callWith(
            UwCallSource.SCHEDULER_SPOT_GEX,
            () -> client.getStockState("SPY")
        );

        assertThat(response).isNotNull();
        assertThat(counter(
            meterRegistry,
            "marketmono.market_data.requests.reserved",
            "operation",
            "stock state",
            "source",
            "scheduler_spot_gex"
        )).isEqualTo(1.0);
        assertThat(counter(
            meterRegistry,
            "marketmono.market_data.requests.completed",
            "operation",
            "stock state",
            "source",
            "scheduler_spot_gex",
            "result",
            "success"
        )).isEqualTo(1.0);
    }

    @Test
    void shouldPublishStoredQuotaRejectionMetricForPreflightBlocks() {
        server.start();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UnusualWhalesClient client = client(2, 100, 10, 0, meterRegistry);

        client.recordRateLimitPreflightRejection("option chains");

        assertThat(counter(
            meterRegistry,
            "marketmono.market_data.requests.rejected",
            "operation",
            "option chains",
            "reason",
            "stored_quota"
        )).isEqualTo(1.0);
    }

    @Test
    void shouldNotRetryMalformedResponses() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, "{not-json");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 3);

        assertThat(client.getStockState("SPY")).isNull();
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void shouldApplyRequestBudgetToRetryAttempts() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 500, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 1, 1);

        assertThat(client.getStockState("SPY")).isNull();
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void shouldForwardFlowAlertCollectionsAsRepeatedUpstreamParams() {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":\"next-cursor\",\"olderThan\":\"previous-cursor\"}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("issue_types[]", List.of("ETF", "Index"));
        params.put("rule_name[]", List.of("RepeatedHits", "FloorTradeSmallCap"));
        params.put("newer_than", "2026-05-15T13:30:00Z");
        params.put("older_than", "2026-05-15T13:00:00Z");

        assertThat(client.getFlowAlerts(params)).isNotNull();

        assertThat(requestCount).hasValue(1);
        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
            .contains("issue_types[]=ETF")
            .contains("issue_types[]=Index")
            .contains("rule_name[]=RepeatedHits")
            .contains("rule_name[]=FloorTradeSmallCap")
            .contains("newer_than=2026-05-15T13:30:00Z")
            .contains("older_than=2026-05-15T13:00:00Z")
            .doesNotContain("issue_types[]=[ETF, Index]")
            .doesNotContain("rule_name[]=[RepeatedHits, FloorTradeSmallCap]");
    }

    @Test
    void shouldFetchStockExpiryBreakdownFromUwStockEndpoint() {
        server.createContext("/api/stock/SPXW/expiry-breakdown", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, """
                {
                  "data": [
                    {
                      "expires": "2026-07-17",
                      "volume": 274967,
                      "open_interest": 2131188,
                      "chains": 1186
                    }
                  ]
                }
                """);
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);

        ExpiryBreakdownResponseDto response = client.getExpiryBreakdownUncached("SPXW");

        assertThat(requestCount).hasValue(1);
        assertThat(response.data()).singleElement().satisfies(item -> {
            assertThat(item.expires()).isEqualTo(java.time.LocalDate.of(2026, 7, 17));
            assertThat(item.volume()).isEqualTo(274_967L);
            assertThat(item.openInterest()).isEqualTo(2_131_188L);
            assertThat(item.chains()).isEqualTo(1_186);
        });
    }

    @Test
    void shouldForwardFlowAlertSpecFiltersToUpstreamUri() {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":null,\"olderThan\":null}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ticker_symbol", "SPY,AAPL,TSLA");
        params.put("min_premium", 100000L);
        params.put("min_size", 25);
        params.put("min_volume", 100);
        params.put("min_open_interest", 500);
        params.put("min_price", 1.25);
        params.put("min_volume_oi_ratio", 0.5);
        params.put("min_size_vol_ratio", 0.25);
        params.put("min_spread", 0.05);
        params.put("min_dte", 1);
        params.put("max_dte", 45);
        params.put("is_call", true);
        params.put("is_put", false);
        params.put("is_sweep", true);
        params.put("is_floor", false);
        params.put("is_multi_leg", false);
        params.put("is_ask_side", true);
        params.put("is_bid_side", false);
        params.put("all_opening", false);
        params.put("is_otm", false);
        params.put("size_greater_oi", false);
        params.put("vol_greater_oi", false);
        params.put("limit", 250);

        assertThat(client.getFlowAlerts(params)).isNotNull();

        assertThat(requestCount).hasValue(1);
        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
            .contains("ticker_symbol=SPY,AAPL,TSLA")
            .contains("min_premium=100000")
            .contains("min_size=25")
            .contains("min_volume=100")
            .contains("min_open_interest=500")
            .contains("min_price=1.25")
            .contains("min_volume_oi_ratio=0.5")
            .contains("min_size_vol_ratio=0.25")
            .contains("min_spread=0.05")
            .contains("min_dte=1")
            .contains("max_dte=45")
            .contains("is_call=true")
            .contains("is_put=false")
            .contains("is_sweep=true")
            .contains("is_floor=false")
            .contains("is_multi_leg=false")
            .contains("is_ask_side=true")
            .contains("is_bid_side=false")
            .contains("all_opening=false")
            .contains("is_otm=false")
            .contains("size_greater_oi=false")
            .contains("vol_greater_oi=false")
            .contains("limit=250");
    }

    @Test
    void shouldFetchUncachedFlowAlertsFromSameUpstreamEndpoint() {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":null,\"olderThan\":null}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("min_premium", 50_000L);
        params.put("min_volume", 100);
        params.put("is_multi_leg", false);
        params.put("limit", 50);

        assertThat(client.getFlowAlertsUncached(params)).isNotNull();

        assertThat(requestCount).hasValue(1);
        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
            .contains("min_premium=50000")
            .contains("min_volume=100")
            .contains("is_multi_leg=false")
            .contains("limit=50");
    }

    @Test
    void shouldUseLockedFlowAlertParamsForBackendPoller() {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":null,\"olderThan\":null}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);

        assertThat(client.getLockedFlowAlerts()).isNotNull();

        assertThat(requestCount).hasValue(1);
        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
            .contains("min_premium=50000")
            .contains("min_size=100")
            .contains("min_volume=100")
            .contains("all_opening=true")
            .contains("is_multi_leg=false")
            .contains("size_greater_oi=true")
            .contains("vol_greater_oi=true")
            .contains("limit=200")
            .doesNotContain("min_dte=")
            .doesNotContain("max_dte=");
    }

    @Test
    void shouldBypassCacheProxyForLockedFlowAlertPoller() {
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":null,\"olderThan\":null}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);
        UnusualWhalesClient selfProxy = mock(UnusualWhalesClient.class);
        client.setSelf(selfProxy);

        assertThat(client.getLockedFlowAlerts()).isNotNull();

        assertThat(requestCount).hasValue(1);
        verifyNoInteractions(selfProxy);
    }

    @Test
    void shouldAddBackfillWindowToLockedFlowAlertParams() {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/api/option-trades/flow-alerts", exchange -> {
            requestCount.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            writeJson(exchange, 200, "{\"data\":[],\"newerThan\":null,\"olderThan\":null}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0);

        assertThat(client.getLockedFlowAlertsBetween(
            "2026-06-25T13:30:00Z",
            "2026-06-25T20:15:00Z"
        )).isNotNull();

        assertThat(requestCount).hasValue(1);
        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
            .contains("min_premium=50000")
            .contains("min_size=100")
            .contains("min_volume=100")
            .contains("all_opening=true")
            .contains("is_multi_leg=false")
            .contains("size_greater_oi=true")
            .contains("vol_greater_oi=true")
            .contains("limit=200")
            .contains("newer_than=2026-06-25T13:30:00Z")
            .contains("older_than=2026-06-25T20:15:00Z");
    }

    @Test
    void shouldSkipAdminUwCallReportingWhenDisabledAndStillReturnUwResponse() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(false, "test-secret"));

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(0);
    }

    @Test
    void shouldSkipAdminUwCallReportingWhenSecretIsMissingAndStillReturnUwResponse() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(true, ""));

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(0);
    }

    @Test
    void shouldReportSuccessfulUwCallToAdminApiWithQuotaHeaders() {
        AtomicReference<String> adminSecret = new AtomicReference<>();
        AtomicReference<String> adminBody = new AtomicReference<>();
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("x-uw-daily-req-count", "8");
            exchange.getResponseHeaders().add("x-uw-token-req-limit", "15000");
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            adminSecret.set(exchange.getRequestHeaders().getFirst("X-Internal-Admin-Secret"));
            adminBody.set(readBody(exchange));
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(true, "test-secret"));

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(adminRequestCount).hasValue(1);
        assertThat(adminSecret).hasValue("test-secret");
        assertThat(adminBody.get())
            .contains("\"endpoint\":\"/api/stock/SPY/stock-state\"")
            .contains("\"source\":\"BACKEND_SERVER\"")
            .contains("\"triggerName\":\"stock state\"")
            .contains("\"httpMethod\":\"GET\"")
            .contains("\"httpStatus\":200")
            .contains("\"durationMs\":")
            .contains("\"dailyReqCount\":8")
            .contains("\"dailyReqLimit\":15000")
            .doesNotContain("test-key")
            .doesNotContain("Authorization");
    }

    @Test
    void shouldNotBreakUwCallWhenAdminReportingReturnsError() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            writeJson(exchange, 500, "{\"error\":\"admin unavailable\"}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(true, "test-secret"));

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(1);
    }

    @Test
    void shouldNotBreakSuccessfulUwResponseWhenReporterThrowsUnexpectedRuntimeException() {
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        server.start();
        UnusualWhalesCallReporter throwingReporter = new UnusualWhalesCallReporter(
            new UnusualWhalesConfig(),
            (MeterRegistry) null
        ) {
            @Override
            public void report(CallReport report) {
                throw new IllegalStateException("reporter failed with test-key");
            }
        };

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, throwingReporter);

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void shouldNotBreakUwCallWhenAdminReportingRejectsBadInternalSecret(int adminStatus) {
        AtomicReference<String> adminBody = new AtomicReference<>();
        String badSecret = "bad-internal-secret";
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("x-uw-daily-req-count", "8");
            exchange.getResponseHeaders().add("x-uw-token-req-limit", "15000");
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            adminBody.set(readBody(exchange));
            writeJson(exchange, adminStatus, "{\"error\":\"unauthorized\"}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(true, badSecret));

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(1);
        assertThat(adminBody.get())
            .contains("\"endpoint\":\"/api/stock/SPY/stock-state\"")
            .contains("\"httpStatus\":200")
            .contains("\"dailyReqCount\":8")
            .doesNotContain(badSecret)
            .doesNotContain("test-key")
            .doesNotContain("Authorization");
    }

    @Test
    void shouldBoundAdminReportingTimeoutAndStillReturnUwResponse() {
        CountDownLatch releaseAdminResponse = new CountDownLatch(1);
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            try {
                releaseAdminResponse.await(1_200, TimeUnit.MILLISECONDS);
                writeJson(exchange, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, "{}");
            }
        });
        server.start();

        UnusualWhalesClient client = client(
            2,
            100,
            100,
            0,
            null,
            false,
            adminReporter(true, "test-secret", 1, 10_000)
        );

        long start = System.nanoTime();
        try {
            assertThat(client.getStockState("SPY")).isNotNull();
        } finally {
            releaseAdminResponse.countDown();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(1);
        assertThat(elapsedMs).isLessThan(800);
    }

    @Test
    void shouldExhaustAdminReportingRetryBudgetWithoutBreakingUwResponse() {
        int configuredMaxAttempts = 5;
        int hardClampedMaxAttempts = 2;
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, stockStateJson());
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            writeJson(exchange, 500, "{\"error\":\"admin unavailable\"}");
        });
        server.start();

        UnusualWhalesClient client = client(
            2,
            100,
            100,
            0,
            null,
            false,
            adminReporter(true, "test-secret", configuredMaxAttempts, 500)
        );

        assertThat(client.getStockState("SPY")).isNotNull();
        assertThat(requestCount).hasValue(1);
        assertThat(adminRequestCount).hasValue(hardClampedMaxAttempts);
    }

    @Test
    void shouldReportHttpErrorWithoutExposingUpstreamBodyOrApiKey() {
        AtomicReference<String> adminBody = new AtomicReference<>();
        String upstreamBody = "{\"error\":\"quota failed with secret test-key\"}";
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("x-uw-daily-req-count", "9");
            exchange.getResponseHeaders().add("x-uw-token-req-limit", "15000");
            writeJson(exchange, 429, upstreamBody);
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            adminBody.set(readBody(exchange));
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(2, 100, 100, 0, null, false, adminReporter(true, "test-secret"));

        assertThat(client.getStockState("SPY")).isNull();
        assertThat(adminRequestCount).hasValue(1);
        assertThat(adminBody.get())
            .contains("\"httpStatus\":429")
            .contains("\"errorMessage\":\"WebClientResponseException\"")
            .contains("\"dailyReqCount\":9")
            .doesNotContain(upstreamBody)
            .doesNotContain("quota failed")
            .doesNotContain("test-key");
    }

    @Test
    void shouldSanitize422BodyBeforeRecordingBlockedEndpointAndReportingToAdmin() {
        AtomicReference<String> adminBody = new AtomicReference<>();
        RateLimitService rateLimitService = mock(RateLimitService.class);
        String upstreamBody = "{\"error\":\"Enterprise-Only Endpoint requires access for test-key fake-secret-value\"}";
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("x-uw-daily-req-count", "10");
            exchange.getResponseHeaders().add("x-uw-token-req-limit", "15000");
            writeJson(exchange, 422, upstreamBody);
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            adminBody.set(readBody(exchange));
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(
            2,
            100,
            100,
            0,
            null,
            false,
            rateLimitService,
            CircuitBreaker.ofDefaults("unusual-whales-test"),
            adminReporter(true, "test-secret")
        );

        assertThat(client.getStockState("SPY")).isNull();
        verify(rateLimitService).recordBlockedEndpoint(
            eq(422),
            eq(RateLimitService.MARKET_DATA_HTTP_422_ACCESS_BLOCKED)
        );
        assertThat(adminRequestCount).hasValue(1);
        assertThat(adminBody.get())
            .contains("\"httpStatus\":422")
            .contains("\"errorMessage\":\"WebClientResponseException\"")
            .contains("\"dailyReqCount\":10")
            .doesNotContain(upstreamBody)
            .doesNotContain("test-key")
            .doesNotContain("fake-secret-value")
            .doesNotContain("Enterprise-Only Endpoint");
    }

    @Test
    void shouldRecordValidation422WithSafeReasonWithoutReportingRawBody() {
        AtomicReference<String> adminBody = new AtomicReference<>();
        RateLimitService rateLimitService = mock(RateLimitService.class);
        String upstreamBody = "{\"error\":\"invalid ticker test-key fake-secret-value\"}";
        server.createContext("/api/stock/SPY/stock-state", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().add("x-uw-daily-req-count", "11");
            exchange.getResponseHeaders().add("x-uw-token-req-limit", "15000");
            writeJson(exchange, 422, upstreamBody);
        });
        startAdminServer(exchange -> {
            adminRequestCount.incrementAndGet();
            adminBody.set(readBody(exchange));
            writeJson(exchange, 200, "{}");
        });
        server.start();

        UnusualWhalesClient client = client(
            2,
            100,
            100,
            0,
            null,
            false,
            rateLimitService,
            CircuitBreaker.ofDefaults("unusual-whales-test"),
            adminReporter(true, "test-secret")
        );

        assertThat(client.getStockState("SPY")).isNull();
        verify(rateLimitService).recordBlockedEndpoint(
            eq(422),
            eq(RateLimitService.MARKET_DATA_HTTP_422_VALIDATION_ERROR)
        );
        assertThat(adminRequestCount).hasValue(1);
        assertThat(adminBody.get())
            .contains("\"httpStatus\":422")
            .contains("\"errorMessage\":\"WebClientResponseException\"")
            .contains("\"dailyReqCount\":11")
            .doesNotContain(upstreamBody)
            .doesNotContain("invalid ticker")
            .doesNotContain("test-key")
            .doesNotContain("fake-secret-value");
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts
    ) {
        return client(maxConcurrentRequests, permitWaitMs, hardCapPerMinute, retryMaxAttempts, null);
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts,
        MeterRegistry meterRegistry
    ) {
        return client(maxConcurrentRequests, permitWaitMs, hardCapPerMinute, retryMaxAttempts, meterRegistry, false);
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts,
        MeterRegistry meterRegistry,
        boolean shed503Enabled
    ) {
        return client(
            maxConcurrentRequests,
            permitWaitMs,
            hardCapPerMinute,
            retryMaxAttempts,
            meterRegistry,
            shed503Enabled,
            mock(RateLimitService.class),
            CircuitBreaker.ofDefaults("unusual-whales-test")
        );
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts,
        MeterRegistry meterRegistry,
        boolean shed503Enabled,
        UnusualWhalesCallReporter callReporter
    ) {
        return client(
            maxConcurrentRequests,
            permitWaitMs,
            hardCapPerMinute,
            retryMaxAttempts,
            meterRegistry,
            shed503Enabled,
            mock(RateLimitService.class),
            CircuitBreaker.ofDefaults("unusual-whales-test"),
            callReporter
        );
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts,
        MeterRegistry meterRegistry,
        boolean shed503Enabled,
        RateLimitService rateLimitService,
        CircuitBreaker circuitBreaker
    ) {
        return client(
            maxConcurrentRequests,
            permitWaitMs,
            hardCapPerMinute,
            retryMaxAttempts,
            meterRegistry,
            shed503Enabled,
            rateLimitService,
            circuitBreaker,
            UnusualWhalesCallReporter.noop()
        );
    }

    private UnusualWhalesClient client(
        int maxConcurrentRequests,
        int permitWaitMs,
        int hardCapPerMinute,
        int retryMaxAttempts,
        MeterRegistry meterRegistry,
        boolean shed503Enabled,
        RateLimitService rateLimitService,
        CircuitBreaker circuitBreaker,
        UnusualWhalesCallReporter callReporter
    ) {
        awaitServerReady();

        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getApi().setBaseUrl("http://" + LOOPBACK_HOST + ":" + server.getAddress().getPort());
        config.getApi().setApiKey("test-key");
        config.getApi().setMaxConcurrentRequests(maxConcurrentRequests);
        config.getApi().getEndpoints().setStockState(true);
        config.getApi().getEndpoints().setFlowAlerts(true);
        config.getApi().getRateLimit().setTargetPerMinute(hardCapPerMinute);
        config.getApi().getRateLimit().setHardCapPerMinute(hardCapPerMinute);
        config.getApi().getTimeout().setRead(5_000);
        config.getApi().getTimeout().setPermitWait(permitWaitMs);
        config.getApi().getRetry().setMaxAttempts(retryMaxAttempts);
        config.getApi().getRetry().setBackoffMultiplier(1);
        UnusualWhalesShed503Properties shed503Properties = new UnusualWhalesShed503Properties();
        shed503Properties.setEnabled(shed503Enabled);

        return new UnusualWhalesClient(
            config,
            circuitBreaker,
            rateLimitService,
            meterRegistry,
            shed503Properties,
            callReporter
        );
    }

    private UnusualWhalesCallReporter adminReporter(boolean enabled, String secret) {
        return adminReporter(enabled, secret, 1, 500);
    }

    private UnusualWhalesCallReporter adminReporter(
        boolean enabled,
        String secret,
        int maxAttempts,
        int readTimeoutMs
    ) {
        UnusualWhalesConfig config = new UnusualWhalesConfig();
        config.getApi().getReporting().setEnabled(enabled);
        config.getApi().getReporting().setAdminApiBaseUrl(
            "http://" + LOOPBACK_HOST + ":" + adminServer.getAddress().getPort()
        );
        config.getApi().getReporting().setInternalSecret(secret);
        config.getApi().getReporting().setConnectTimeout(250);
        config.getApi().getReporting().setReadTimeout(readTimeoutMs);
        config.getApi().getReporting().setMaxAttempts(maxAttempts);
        return new UnusualWhalesCallReporter(config, (MeterRegistry) null);
    }

    private void startAdminServer(AdminHandler handler) {
        try {
            adminServer = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
            adminServerExecutor = Executors.newCachedThreadPool();
            adminServer.setExecutor(adminServerExecutor);
            adminServer.createContext("/admin-api/internal/uw-api/calls", handler::handle);
            adminServer.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void awaitServerReady() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(
                    "http://" + LOOPBACK_HOST + ":" + server.getAddress().getPort() + "/__ready"
                ).toURL().openConnection();
                connection.setConnectTimeout(100);
                connection.setReadTimeout(100);
                if (connection.getResponseCode() == 200) {
                    connection.disconnect();
                    return;
                }
                connection.disconnect();
            } catch (IOException ignored) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
                if (Thread.currentThread().isInterrupted()) {
                    throw new AssertionError("Interrupted while waiting for test HTTP server");
                }
            }
        }
        throw new AssertionError("Test HTTP server did not become ready");
    }

    private static double counter(MeterRegistry meterRegistry, String name, String... tags) {
        return meterRegistry.find(name).tags(tags).counter().count();
    }

    private static double gauge(MeterRegistry meterRegistry, String name) {
        return meterRegistry.find(name).gauge().value();
    }

    private static String stockStateJson() {
        return """
            {
              "data": {
                "close": "500.00",
                "prev_close": "499.00",
                "tape_time": "2026-05-14T14:30:00Z",
                "volume": 1000,
                "total_volume": 2000,
                "open": "498.00",
                "high": "501.00",
                "low": "497.00",
                "market_time": "market"
              }
            }
            """;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (var output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    @FunctionalInterface
    private interface AdminHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
