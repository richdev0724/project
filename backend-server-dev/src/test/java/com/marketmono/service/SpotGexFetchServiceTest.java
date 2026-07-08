package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.dto.ExpiryBreakdownDto;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import com.marketmono.dto.OptionChainsResponseDto;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.dto.SpotExposureResponseDto;
import com.marketmono.exception.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexFetchServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private RateLimitService rateLimitService;

    @Spy
    private StrataTickerFamilyResolver strataTickerFamilyResolver = new StrataTickerFamilyResolver();

    @InjectMocks
    private SpotGexFetchService spotGexFetchService;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        SpotGexCollectionProperties properties = new SpotGexCollectionProperties();
        properties.setSource(SpotGexCollectionProperties.SourceMode.STREAM);
        ReflectionTestUtils.setField(spotGexFetchService, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(spotGexFetchService, "spotGexCollectionProperties", properties);
    }

    @Test
    void shouldUseExpiryBreakdownForSpxFamilyExpirationDates() {
        when(unusualWhalesClient.getExpiryBreakdownUncached("SPXW")).thenReturn(
            new ExpiryBreakdownResponseDto(List.of(
                new ExpiryBreakdownDto(LocalDate.of(2026, 7, 17), 274_967L, 2_131_188L, 1_186),
                new ExpiryBreakdownDto(LocalDate.of(2026, 8, 21), 122_614L, 1_808_580L, 1_052),
                new ExpiryBreakdownDto(LocalDate.of(2026, 6, 18), 0L, 6_265_639L, 1_278)
            ))
        );

        List<LocalDate> expirations = spotGexFetchService.getExpirationDates("spxw", null);

        assertThat(expirations).containsExactly(
            LocalDate.of(2026, 6, 18),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 8, 21)
        );
        verify(unusualWhalesClient).getExpiryBreakdownUncached("SPXW");
        verify(unusualWhalesClient, never()).getOptionChainsUncached("SPXW", null);
        assertThat(restRequestCounter("expiry_breakdown", "success", "stream")).isEqualTo(1.0d);
    }

    @Test
    void shouldUseCombinedExpiryBreakdownForVixFamilyExpirationDates() {
        when(unusualWhalesClient.getExpiryBreakdownUncached("VIXW")).thenReturn(
            new ExpiryBreakdownResponseDto(List.of(
                new ExpiryBreakdownDto(LocalDate.of(2026, 6, 24), 19_805L, 89_924L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 7, 1), 9_164L, 23_115L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 7, 8), 2_189L, 13_934L, 98),
                new ExpiryBreakdownDto(LocalDate.of(2026, 7, 15), 1_930L, 5_648L, 100)
            ))
        );
        when(unusualWhalesClient.getExpiryBreakdownUncached("VIX")).thenReturn(
            new ExpiryBreakdownResponseDto(List.of(
                new ExpiryBreakdownDto(LocalDate.of(2026, 7, 22), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 8, 19), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 9, 16), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 10, 21), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 11, 18), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2026, 12, 16), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2027, 1, 20), 12_345L, 90_000L, 100),
                new ExpiryBreakdownDto(LocalDate.of(2027, 2, 17), 12_345L, 90_000L, 100)
            ))
        );

        List<LocalDate> expirations = spotGexFetchService.getExpirationDates("vixw", null);

        assertThat(expirations).containsExactly(
            LocalDate.of(2026, 6, 24),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 22),
            LocalDate.of(2026, 8, 19),
            LocalDate.of(2026, 9, 16),
            LocalDate.of(2026, 10, 21),
            LocalDate.of(2026, 11, 18),
            LocalDate.of(2026, 12, 16),
            LocalDate.of(2027, 1, 20),
            LocalDate.of(2027, 2, 17)
        );
        verify(unusualWhalesClient).getExpiryBreakdownUncached("VIXW");
        verify(unusualWhalesClient).getExpiryBreakdownUncached("VIX");
        verify(unusualWhalesClient, never()).getOptionChainsUncached("VIXW", null);
        assertThat(restRequestCounter("expiry_breakdown", "success", "stream")).isEqualTo(2.0d);
    }

    @Test
    void shouldExtractUniqueExpirationDatesFromOptionChains() {
        when(unusualWhalesClient.getOptionChainsUncached("SPY", null)).thenReturn(
            new OptionChainsResponseDto(List.of(
                "SPY260417C00500000",
                "SPY260417P00500000",
                "SPY260425C00505000"
            ))
        );

        List<LocalDate> expirations = spotGexFetchService.getExpirationDates("SPY", null);

        assertThat(expirations).containsExactly(
            LocalDate.of(2026, 4, 17),
            LocalDate.of(2026, 4, 25)
        );
        verify(rateLimitService).checkRateLimit();
        assertThat(restRequestCounter("option_chains", "success", "stream")).isEqualTo(1.0d);
    }

    @Test
    void shouldCacheExpirationDatesForRepeatedTickerLookup() {
        when(unusualWhalesClient.getOptionChainsUncached("SPY", null)).thenReturn(
            new OptionChainsResponseDto(List.of(
                "SPY260417C00500000",
                "SPY260425P00500000"
            ))
        );

        List<LocalDate> first = spotGexFetchService.getExpirationDates("SPY", null);
        List<LocalDate> second = spotGexFetchService.getExpirationDates("spy", null);

        assertThat(first).containsExactly(LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 25));
        assertThat(second).containsExactly(LocalDate.of(2026, 4, 17), LocalDate.of(2026, 4, 25));
        verify(unusualWhalesClient, times(1)).getOptionChainsUncached("SPY", null);
        verify(rateLimitService, times(1)).checkRateLimit();
    }

    @Test
    void shouldRecordPreflightQuotaRejectionWhenOptionChainRateLimitBlocksFetch() {
        doThrow(new RateLimitExceededException("stored quota exhausted"))
            .when(rateLimitService)
            .checkRateLimit();

        assertThatThrownBy(() -> spotGexFetchService.getExpirationDates("SPY", null))
            .isInstanceOf(RateLimitExceededException.class);

        verify(unusualWhalesClient).recordRateLimitPreflightRejection("option chains");
        verify(unusualWhalesClient, never()).getOptionChainsUncached("SPY", null);
    }

    @Test
    void shouldFetchPaginatedSpotExposureUntilEmptyPage() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        SpotExposureByStrikeDto firstRow = sampleRow(new BigDecimal("500.0000"));
        SpotExposureByStrikeDto secondRow = sampleRow(new BigDecimal("505.0000"));

        when(unusualWhalesClient.getSpotExposureByExpiryStrikeUncached(
            "SPY", List.of("2026-04-17"), null, 2, 0, null, null, null, null
        )).thenReturn(new SpotExposureResponseDto(List.of(firstRow, secondRow)));

        when(unusualWhalesClient.getSpotExposureByExpiryStrikeUncached(
            "SPY", List.of("2026-04-17"), null, 2, 1, null, null, null, null
        )).thenReturn(new SpotExposureResponseDto(List.of()));

        List<SpotExposureByStrikeDto> result = spotGexFetchService.fetchAllSpotExposurePages("SPY", expirations, null, 2);

        assertThat(result).containsExactly(firstRow, secondRow);
        assertThat(restRequestCounter("spot_exposure_by_expiry_strike", "success", "stream")).isEqualTo(2.0d);
        verify(unusualWhalesClient).getSpotExposureByExpiryStrikeUncached(
            "SPY", List.of("2026-04-17"), null, 2, 0, null, null, null, null
        );
        verify(unusualWhalesClient).getSpotExposureByExpiryStrikeUncached(
            "SPY", List.of("2026-04-17"), null, 2, 1, null, null, null, null
        );
        verify(rateLimitService, times(2)).checkRateLimit();
    }

    @Test
    void shouldRecordPreflightQuotaRejectionWhenSpotExposureRateLimitBlocksPageFetch() {
        List<LocalDate> expirations = List.of(LocalDate.of(2026, 4, 17));
        doThrow(new RateLimitExceededException("stored quota exhausted"))
            .when(rateLimitService)
            .checkRateLimit();

        assertThatThrownBy(() -> spotGexFetchService.fetchAllSpotExposurePages("SPY", expirations, null, 2))
            .isInstanceOf(RateLimitExceededException.class);

        verify(unusualWhalesClient).recordRateLimitPreflightRejection("spot exposure by expiry strike");
        verify(unusualWhalesClient, never()).getSpotExposureByExpiryStrikeUncached(
            "SPY", List.of("2026-04-17"), null, 2, 0, null, null, null, null
        );
    }

    private SpotExposureByStrikeDto sampleRow(BigDecimal strike) {
        return new SpotExposureByStrikeDto(
            "SPY",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.of(2026, 4, 6),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("502.1200"),
            BigDecimal.ZERO,
            strike,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.of(2026, 4, 17)
        );
    }

    private double restRequestCounter(String operation, String result, String sourceMode) {
        return meterRegistry.get("marketmono.spot.gex.rest.requests")
            .tag("operation", operation)
            .tag("result", result)
            .tag("source_mode", sourceMode)
            .counter()
            .count();
    }
}
