package com.marketmono.service;

import com.marketmono.entity.SpotGexExposure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexExposureBatchPersistenceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private SpotGexExposureBatchPersistence persistence;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        persistence = new SpotGexExposureBatchPersistence(jdbcTemplate, meterRegistry);
        persistence.initMetrics();
    }

    @Test
    void shouldPersistBatchAndTrackDuplicates() {
        List<SpotGexExposure> exposures = List.of(
            sampleExposure("SPY", new BigDecimal("500.0000")),
            sampleExposure("SPY", new BigDecimal("505.0000"))
        );

        when(jdbcTemplate.batchUpdate(anyString(), eq(exposures), eq(exposures.size()), any()))
            .thenReturn(new int[][]{{1, 0}});

        SpotGexExposureBatchPersistence.BatchPersistResult result = persistence.persistBatch(exposures);

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(meterRegistry.get("spot_gex.persisted").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.duplicates_skipped").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("spot_gex.failed").counter().count()).isEqualTo(0.0d);
    }

    @Test
    void shouldSkipJdbcCallWhenBatchIsEmpty() {
        SpotGexExposureBatchPersistence.BatchPersistResult result = persistence.persistBatch(List.of());

        assertThat(result.attempted()).isZero();
        assertThat(result.inserted()).isZero();
        assertThat(result.skipped()).isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(), anyInt(), any());
    }

    private SpotGexExposure sampleExposure(String ticker, BigDecimal strike) {
        return SpotGexExposure.builder()
            .ticker(ticker)
            .expiry(LocalDate.of(2026, 4, 17))
            .strike(strike)
            .collectedAt(Instant.parse("2026-04-06T14:30:00Z"))
            .price(new BigDecimal("502.1200"))
            .marketDate(LocalDate.of(2026, 4, 6))
            .sourceTime(Instant.parse("2026-04-06T14:29:58Z"))
            .callGammaOi(new BigDecimal("12345.6789"))
            .putGammaOi(new BigDecimal("-6789.1234"))
            .build();
    }
}
