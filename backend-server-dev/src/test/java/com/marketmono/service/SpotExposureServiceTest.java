package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.SpotExposureByStrikeDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotExposureServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private SpotGexQueryService spotGexQueryService;

    @Mock
    private CollectionConfigService collectionConfigService;

    @Spy
    private StrataTickerFamilyResolver strataTickerFamilyResolver = new StrataTickerFamilyResolver();

    @Spy
    private SpotGexTickerSourcePolicy tickerSourcePolicy = new SpotGexTickerSourcePolicy(strataTickerFamilyResolver);

    @InjectMocks
    private SpotExposureService spotExposureService;

    @Test
    void expiryStrikeExposureUsesPersistedSnapshotAndDoesNotCallUnusualWhales() {
        SpotGexSnapshotDto snapshot = sampleSnapshot("SPY", LocalDate.of(2026, 4, 17), decimal("500.0000"));
        when(spotGexQueryService.getLatestSnapshot("SPY", LocalDate.of(2026, 4, 17), decimal("400"), decimal("600")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-04-06T14:30:00Z"),
                List.of(snapshot)
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spy",
            List.of("2026-04-17"),
            null,
            50,
            0,
            400,
            600,
            null,
            null
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().ticker()).isEqualTo("SPY");
        assertThat(response.getFirst().expiry()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(response.getFirst().strike()).isEqualByComparingTo("500.0000");
        verify(spotGexQueryService).getLatestSnapshot("SPY", LocalDate.of(2026, 4, 17), decimal("400"), decimal("600"));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void expiryStrikeExposureReturnsEmptySnapshotResultWithoutUnusualWhalesFallback() {
        when(spotGexQueryService.getLatestSnapshot("SPY", null, null, null))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(null, List.of()));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spy",
            List.of(),
            null,
            50,
            0,
            null,
            null,
            null,
            null
        );

        assertThat(response).isEmpty();
        verify(spotGexQueryService).getLatestSnapshot("SPY", null, null, null);
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void expiryStrikeExposureUsesStoredSpxwRootForSpxDisplayTicker() {
        SpotGexSnapshotDto snapshot = sampleSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("750.0000"));
        when(spotGexQueryService.getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of(snapshot)
            ));
        when(spotGexQueryService.getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of()
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spx",
            List.of("2026-06-18"),
            null,
            50,
            0,
            700,
            800,
            null,
            null
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().ticker()).isEqualTo("SPX");
        assertThat(response.getFirst().expiry()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.getFirst().strike()).isEqualByComparingTo("750.0000");
        verify(spotGexQueryService).getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verify(spotGexQueryService).getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void expiryStrikeExposureMergesFamilyRootsForRequestedExpirations() {
        SpotGexSnapshotDto weeklySnapshot = sampleSnapshot("SPXW", LocalDate.of(2026, 6, 24), decimal("750.0000"));
        SpotGexSnapshotDto monthlySnapshot = sampleSnapshot("SPX", LocalDate.of(2026, 7, 17), decimal("752.0000"));
        when(spotGexQueryService.getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 24), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of(weeklySnapshot)
            ));
        when(spotGexQueryService.getLatestSnapshot("SPXW", LocalDate.of(2026, 7, 17), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of()
            ));
        when(spotGexQueryService.getLatestSnapshot("SPX", LocalDate.of(2026, 6, 24), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of()
            ));
        when(spotGexQueryService.getLatestSnapshot("SPX", LocalDate.of(2026, 7, 17), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of(monthlySnapshot)
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spx",
            List.of("2026-06-24", "2026-07-17"),
            null,
            50,
            0,
            700,
            800,
            null,
            null
        );

        assertThat(response).extracting(SpotExposureByStrikeDto::ticker)
            .containsExactly("SPX", "SPX");
        assertThat(response).extracting(SpotExposureByStrikeDto::expiry)
            .containsExactly(LocalDate.of(2026, 6, 24), LocalDate.of(2026, 7, 17));
        assertThat(response).extracting(SpotExposureByStrikeDto::strike)
            .containsExactly(decimal("750.0000"), decimal("752.0000"));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void expiryStrikeExposureDoesNotDemotePreferredSpxwRootToConfiguredFallbackTicker() {
        SpotGexSnapshotDto snapshot = sampleSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("750.0000"));
        when(spotGexQueryService.getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of(snapshot)
            ));
        when(spotGexQueryService.getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of()
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spxw",
            List.of("2026-06-18"),
            null,
            50,
            0,
            700,
            800,
            null,
            null
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().ticker()).isEqualTo("SPXW");
        assertThat(response.getFirst().expiry()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.getFirst().strike()).isEqualByComparingTo("750.0000");
        verify(spotGexQueryService).getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verify(spotGexQueryService).getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void spxDisplayTickerUsesPreferredSpxwRootWhenOnlyFallbackRootIsConfigured() {
        SpotGexSnapshotDto snapshot = sampleSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("750.0000"));
        when(spotGexQueryService.getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of(snapshot)
            ));
        when(spotGexQueryService.getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800")))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-18T15:48:00Z"),
                List.of()
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByExpiryStrike(
            "spx",
            List.of("2026-06-18"),
            null,
            50,
            0,
            700,
            800,
            null,
            null
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().ticker()).isEqualTo("SPX");
        assertThat(response.getFirst().expiry()).isEqualTo(LocalDate.of(2026, 6, 18));
        assertThat(response.getFirst().strike()).isEqualByComparingTo("750.0000");
        verify(spotGexQueryService).getLatestSnapshot("SPXW", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verify(spotGexQueryService).getLatestSnapshot("SPX", LocalDate.of(2026, 6, 18), decimal("700"), decimal("800"));
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void restOnlyStrikeExposureKeepsAllFamilyRootsWhenOnlyPreferredRootIsConfigured() {
        SpotGexSnapshotDto weeklySnapshot = sampleSnapshot("VIXW", LocalDate.of(2026, 6, 24), decimal("20.0000"));
        SpotGexSnapshotDto monthlySnapshot = sampleSnapshot("VIX", LocalDate.of(2026, 7, 17), decimal("21.0000"));
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("VIXW"));
        when(spotGexQueryService.getLatestSnapshot("VIXW", null, null, null))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of(weeklySnapshot)
            ));
        when(spotGexQueryService.getLatestSnapshot("VIX", null, null, null))
            .thenReturn(new SpotGexQueryService.LatestSnapshotResult(
                Instant.parse("2026-06-20T15:48:00Z"),
                List.of(monthlySnapshot)
            ));

        List<SpotExposureByStrikeDto> response = spotExposureService.getSpotExposureByStrike(
            "vix",
            null,
            50,
            null
        );

        assertThat(response).extracting(SpotExposureByStrikeDto::ticker)
            .containsExactly("VIX", "VIX");
        assertThat(response).extracting(SpotExposureByStrikeDto::strike)
            .containsExactly(decimal("20.0000"), decimal("21.0000"));
        verify(spotGexQueryService).getLatestSnapshot("VIXW", null, null, null);
        verify(spotGexQueryService).getLatestSnapshot("VIX", null, null, null);
        verifyNoInteractions(unusualWhalesClient);
    }

    private static SpotGexSnapshotDto sampleSnapshot(String ticker, LocalDate expiry, BigDecimal strike) {
        return new SpotGexSnapshotDto(
            ticker,
            expiry,
            strike,
            Instant.parse("2026-04-06T14:30:00Z"),
            decimal("502.1200"),
            LocalDate.of(2026, 4, 6),
            Instant.parse("2026-04-06T14:29:59Z"),
            decimal("1.10"),
            decimal("2.10"),
            decimal("3.10"),
            decimal("4.10"),
            decimal("5.10"),
            decimal("6.10"),
            decimal("7.10"),
            decimal("8.10"),
            decimal("9.10"),
            decimal("10.10"),
            decimal("11.10"),
            decimal("12.10"),
            decimal("13.10"),
            decimal("14.10"),
            decimal("15.10"),
            decimal("16.10"),
            decimal("17.10"),
            decimal("18.10"),
            decimal("19.10"),
            decimal("20.10"),
            decimal("21.10"),
            decimal("22.10"),
            decimal("23.10"),
            decimal("24.10"),
            decimal("25.10"),
            decimal("26.10"),
            decimal("27.10"),
            decimal("28.10"),
            decimal("29.10"),
            decimal("30.10"),
            decimal("31.10"),
            decimal("32.10")
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
