package com.marketmono.service;

import com.marketmono.entity.ApiRateLimit;
import com.marketmono.exception.RateLimitExceededException;
import com.marketmono.repository.ApiRateLimitRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private ApiRateLimitRepository rateLimitRepository;

    @Test
    void apiRateLimitWriteMethodsUseIndependentTransactions() throws NoSuchMethodException {
        assertRequiresNew("checkRateLimit");
        assertRequiresNew("recordSuccessfulRequest", HttpHeaders.class);
        assertRequiresNew("recordRateLimitHit", HttpHeaders.class);
        assertRequiresNew("recordRateLimitHit");
        assertRequiresNew("recordBlockedEndpoint", int.class, String.class);
        assertRequiresNew("getCurrentRateLimitStatus");
        assertRequiresNew("getDailyProjectedUsage");
        assertRequiresNew("isDailyProjectionAtOrAbove", int.class);
    }

    @Test
    void successfulRequestWithoutHeadersIncrementsDailyCount() {
        ApiRateLimit rateLimit = activeLimit();
        RateLimitService service = serviceWithActiveLimit(rateLimit);

        service.recordSuccessfulRequest(new HttpHeaders());

        assertThat(rateLimit.getRequestCount()).isEqualTo(3);
        assertThat(rateLimit.getLimitExceeded()).isFalse();
        verify(rateLimitRepository).save(rateLimit);
    }

    @Test
    void successfulRequestAppliesHeaderSnapshot() {
        ApiRateLimit rateLimit = activeLimit();
        RateLimitService service = serviceWithActiveLimit(rateLimit);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-uw-daily-req-count", "8");
        headers.set("x-uw-token-req-limit", "20");
        headers.set("x-uw-minute-req-counter", "7");
        headers.set("x-uw-req-per-minute-remaining", "53");
        headers.set("x-uw-req-per-minute-reset", "1234567890");

        service.recordSuccessfulRequest(headers);

        assertThat(rateLimit.getRequestCount()).isEqualTo(8);
        assertThat(rateLimit.getDailyLimit()).isEqualTo(20);
        assertThat(rateLimit.getMinuteRequestCount()).isEqualTo(7);
        assertThat(rateLimit.getMinuteLimitRemaining()).isEqualTo(53);
        assertThat(rateLimit.getMinuteLimit()).isEqualTo(60);
        assertThat(rateLimit.getMinuteResetEpoch()).isEqualTo(1234567890L);
        assertThat(rateLimit.getLimitExceeded()).isFalse();
        verify(rateLimitRepository).save(rateLimit);
    }

    @Test
    void dailyQuotaGaugesTrackObservedUsageLimitAndRemaining() {
        ApiRateLimit rateLimit = activeLimit();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RateLimitService service = serviceWithActiveLimit(rateLimit, meterRegistry);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-uw-daily-req-count", "8");
        headers.set("x-uw-token-req-limit", "20");

        service.recordSuccessfulRequest(headers);

        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.daily.usage")).isEqualTo(8.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.daily.limit")).isEqualTo(20.0);
        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.daily.remaining")).isEqualTo(12.0);
    }

    @Test
    void dailyProjectionGaugeExtrapolatesUsageAcrossCurrentWindow() {
        ApiRateLimit rateLimit = activeLimit();
        rateLimit.setRequestCount(25);
        rateLimit.setDailyLimit(150);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RateLimitService service = serviceWithActiveLimit(rateLimit, meterRegistry);

        service.getCurrentRateLimitStatus();

        assertThat(gauge(meterRegistry, "marketmono.market_data.budget.daily.projected.usage"))
            .isGreaterThanOrEqualTo(25.0);
    }

    @Test
    void dailyProjectionCanBeQueriedForBudgetGates() {
        ApiRateLimit rateLimit = activeLimit();
        rateLimit.setRequestCount(25);
        rateLimit.setDailyLimit(150);
        RateLimitService service = serviceWithActiveLimit(rateLimit);

        assertThat(service.getDailyProjectedUsage()).isGreaterThanOrEqualTo(25);
        assertThat(service.isDailyProjectionAtOrAbove(25)).isTrue();
        assertThat(service.isDailyProjectionAtOrAbove(Integer.MAX_VALUE)).isFalse();
    }

    @Test
    void staleMinuteLimitStateIsClearedBeforeAllowingRequest() {
        ApiRateLimit rateLimit = activeLimit();
        rateLimit.setMinuteRequestCount(60);
        rateLimit.setMinuteLimitRemaining(0);
        rateLimit.setMinuteLimit(60);
        rateLimit.setMinuteResetEpoch((System.currentTimeMillis() / 1000) - 60);
        RateLimitService service = serviceWithActiveLimit(rateLimit);

        service.checkRateLimit();

        assertThat(rateLimit.getMinuteRequestCount()).isNull();
        assertThat(rateLimit.getMinuteLimitRemaining()).isNull();
        assertThat(rateLimit.getMinuteLimit()).isNull();
        assertThat(rateLimit.getMinuteResetEpoch()).isNull();
        verify(rateLimitRepository).save(rateLimit);
    }

    @Test
    void rateLimitHitPersists429StateAndHeaderSnapshot() {
        ApiRateLimit rateLimit = activeLimit();
        RateLimitService service = serviceWithActiveLimit(rateLimit);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-uw-daily-req-count", "20");
        headers.set("x-uw-token-req-limit", "20");
        headers.set("x-uw-minute-req-counter", "59");
        headers.set("x-uw-req-per-minute-remaining", "1");

        service.recordRateLimitHit(headers);

        assertThat(rateLimit.getRequestCount()).isEqualTo(20);
        assertThat(rateLimit.getDailyLimit()).isEqualTo(20);
        assertThat(rateLimit.getMinuteRequestCount()).isEqualTo(59);
        assertThat(rateLimit.getMinuteLimit()).isEqualTo(60);
        assertThat(rateLimit.getMinuteLimitRemaining()).isZero();
        assertThat(rateLimit.getLimitExceeded()).isTrue();
        verify(rateLimitRepository).save(rateLimit);
    }

    @Test
    void blockedEndpointPersistsPermanentAccessBlock() {
        ApiRateLimit rateLimit = activeLimit();
        RateLimitService service = serviceWithActiveLimit(rateLimit);

        service.recordBlockedEndpoint(422, RateLimitService.MARKET_DATA_HTTP_422_ACCESS_BLOCKED);

        assertThat(rateLimit.getIsBlocked()).isTrue();
        assertThat(rateLimit.getHttpStatusCode()).isEqualTo(422);
        assertThat(rateLimit.getBlockedReason()).isEqualTo(RateLimitService.MARKET_DATA_HTTP_422_ACCESS_BLOCKED);
        verify(rateLimitRepository).save(rateLimit);
    }

    @Test
    void blockedEndpointDoesNotPersistPermanentBlockForValidation422Reason() {
        RateLimitService service = service();

        service.recordBlockedEndpoint(422, RateLimitService.MARKET_DATA_HTTP_422_VALIDATION_ERROR);

        verifyNoInteractions(rateLimitRepository);
    }

    @Test
    void exceededDailyLimitThrowsRateLimitException() {
        ApiRateLimit rateLimit = activeLimit();
        rateLimit.setRequestCount(10);
        rateLimit.setDailyLimit(10);
        RateLimitService service = serviceWithActiveLimit(rateLimit);

        assertThatThrownBy(service::checkRateLimit)
            .isInstanceOf(RateLimitExceededException.class)
            .hasMessageContaining("10/10");
    }

    private static void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional transactional = RateLimitService.class
            .getDeclaredMethod(methodName, parameterTypes)
            .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private RateLimitService serviceWithActiveLimit(ApiRateLimit rateLimit) {
        when(rateLimitRepository.findActiveRateLimit(any(String.class), any(LocalDateTime.class)))
            .thenReturn(Optional.of(rateLimit));
        lenient().when(rateLimitRepository.save(any(ApiRateLimit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        return service();
    }

    private RateLimitService serviceWithActiveLimit(ApiRateLimit rateLimit, SimpleMeterRegistry meterRegistry) {
        when(rateLimitRepository.findActiveRateLimit(any(String.class), any(LocalDateTime.class)))
            .thenReturn(Optional.of(rateLimit));
        lenient().when(rateLimitRepository.save(any(ApiRateLimit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        return service(meterRegistry);
    }

    private RateLimitService service() {
        RateLimitService service = new RateLimitService(rateLimitRepository);
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "configuredDailyLimit", 10);
        return service;
    }

    private RateLimitService service(SimpleMeterRegistry meterRegistry) {
        RateLimitService service = service();
        ReflectionTestUtils.setField(service, "meterRegistry", meterRegistry);
        ReflectionTestUtils.invokeMethod(service, "registerDailyQuotaGauges");
        return service;
    }

    private static double gauge(SimpleMeterRegistry meterRegistry, String name) {
        return meterRegistry.find(name).gauge().value();
    }

    private static ApiRateLimit activeLimit() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = currentServiceWindowStart(now);
        return ApiRateLimit.builder()
            .apiKeyHash("stored-hash")
            .requestCount(2)
            .dailyLimit(10)
            .limitExceeded(false)
            .isBlocked(false)
            .windowStart(windowStart)
            .windowEnd(windowStart.plusDays(1))
            .build();
    }

    private static LocalDateTime currentServiceWindowStart(LocalDateTime now) {
        ZoneId estZone = ZoneId.of("America/New_York");
        ZonedDateTime nowEst = now.atZone(estZone);
        ZonedDateTime currentWindowEnd = nowEst.toLocalDate().atTime(20, 0).atZone(estZone);
        if (nowEst.isAfter(currentWindowEnd)) {
            currentWindowEnd = currentWindowEnd.plusDays(1);
        }
        return currentWindowEnd.minusDays(1).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
