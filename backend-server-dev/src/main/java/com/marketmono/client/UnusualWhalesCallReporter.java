package com.marketmono.client;

import com.marketmono.config.UnusualWhalesConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Locale;

@Component
public class UnusualWhalesCallReporter {

    private static final Logger log = LoggerFactory.getLogger(UnusualWhalesCallReporter.class);
    private static final String ADMIN_SECRET_HEADER = "X-Internal-Admin-Secret";
    private static final String ADMIN_CALL_PATH = "/admin-api/internal/uw-api/calls";
    private static final String REPORTING_FAILURE_METRIC = "marketmono.market_data.reporting.failures";
    private static final String REPORTING_SUCCESS_METRIC = "marketmono.market_data.reporting.success";
    private static final int MAX_CONNECT_TIMEOUT_MS = 250;
    private static final int MAX_READ_TIMEOUT_MS = 500;
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_REASON_TAG_LENGTH = 64;
    private static final UnusualWhalesCallReporter NOOP = new UnusualWhalesCallReporter(
        null,
        (MeterRegistry) null
    );

    private final UnusualWhalesConfig.Reporting reporting;
    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    @Autowired
    public UnusualWhalesCallReporter(
        UnusualWhalesConfig config,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this(
            config,
            meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null
        );
    }

    UnusualWhalesCallReporter(UnusualWhalesConfig config, MeterRegistry meterRegistry) {
        this.reporting = config != null && config.getApi() != null
            ? config.getApi().getReporting()
            : new UnusualWhalesConfig.Reporting();
        this.meterRegistry = meterRegistry;
        this.webClient = buildWebClient(this.reporting);
    }

    public static UnusualWhalesCallReporter noop() {
        return NOOP;
    }

    public void report(CallReport report) {
        if (!isReady()) {
            return;
        }

        int attempts = maxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                webClient.post()
                    .uri(ADMIN_CALL_PATH)
                    .header(ADMIN_SECRET_HEADER, reporting.getInternalSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(report)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(readTimeoutMs()));
                incrementSuccess();
                return;
            } catch (WebClientResponseException e) {
                if (attempt >= attempts) {
                    log.warn(
                        "Market data call reporting failed with admin status {} for trigger={} endpoint={}",
                        e.getStatusCode().value(),
                        report.triggerName(),
                        report.endpoint()
                    );
                    incrementFailure("admin_" + e.getStatusCode().value());
                }
            } catch (RuntimeException e) {
                if (attempt >= attempts) {
                    log.warn(
                        "Market data call reporting failed with {} for trigger={} endpoint={}",
                        e.getClass().getSimpleName(),
                        report.triggerName(),
                        report.endpoint()
                    );
                    incrementFailure(errorTag(e.getClass().getSimpleName()));
                }
            }
        }
    }

    private boolean isReady() {
        return reporting.isEnabled()
            && hasText(reporting.getAdminApiBaseUrl())
            && hasText(reporting.getInternalSecret());
    }

    private WebClient buildWebClient(UnusualWhalesConfig.Reporting properties) {
        if (properties == null || !hasText(properties.getAdminApiBaseUrl())) {
            return null;
        }
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, boundedTimeout(properties.getConnectTimeout(), MAX_CONNECT_TIMEOUT_MS))
            .responseTimeout(Duration.ofMillis(boundedTimeout(properties.getReadTimeout(), MAX_READ_TIMEOUT_MS)));

        return WebClient.builder()
            .baseUrl(properties.getAdminApiBaseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private long readTimeoutMs() {
        return boundedTimeout(reporting.getReadTimeout(), MAX_READ_TIMEOUT_MS);
    }

    private int maxAttempts() {
        return Math.max(1, Math.min(reporting.getMaxAttempts(), MAX_ATTEMPTS));
    }

    private int boundedTimeout(int configuredMs, int maxMs) {
        return Math.max(1, Math.min(configuredMs, maxMs));
    }

    private void incrementSuccess() {
        if (meterRegistry != null) {
            Counter.builder(REPORTING_SUCCESS_METRIC).register(meterRegistry).increment();
        }
    }

    private void incrementFailure(String reason) {
        if (meterRegistry != null) {
            Counter.builder(REPORTING_FAILURE_METRIC)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
        }
    }

    private String errorTag(String reason) {
        if (!hasText(reason)) {
            return "unknown";
        }
        String sanitized = reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return sanitized.length() <= MAX_REASON_TAG_LENGTH
            ? sanitized
            : sanitized.substring(0, MAX_REASON_TAG_LENGTH);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CallReport(
        String endpoint,
        String source,
        String triggerName,
        String httpMethod,
        Integer httpStatus,
        long durationMs,
        String errorMessage,
        Integer dailyReqCount,
        Integer dailyReqLimit
    ) {
    }
}
