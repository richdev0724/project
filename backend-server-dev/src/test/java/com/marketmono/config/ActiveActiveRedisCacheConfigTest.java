package com.marketmono.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.DarkPoolRecentResponseDto;
import com.marketmono.dto.DarkPoolRecentTradeDto;
import com.marketmono.dto.SpotGexSnapshotDto;
import com.marketmono.dto.TopNetImpactResponse;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.dto.UnusualWhalesMarketTideDto;
import com.marketmono.dto.UnusualWhalesOhlcDto;
import com.marketmono.service.SpotGexQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveActiveRedisCacheConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldRoundTripFinalResponseRecordsThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);

        TopNetImpactResponse original = TopNetImpactResponse.success(
            new TopNetImpactResponse.TopNetImpactData(
                List.of(new TopNetImpactResponse.TopNetImpactTicker("SPY", 1_250_000.50)),
                List.of(new TopNetImpactResponse.TopNetImpactTicker("QQQ", -980_000.25))
            )
        );

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored)
            .isInstanceOf(TopNetImpactResponse.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRoundTripFinalNestedRecordListsThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);

        UnusualWhalesOhlcDto original = new UnusualWhalesOhlcDto(List.of(
            new UnusualWhalesOhlcDto.OhlcItem(
                "2026-04-14T13:30:00Z",
                "2026-04-14T13:31:00Z",
                null,
                "510.10",
                "511.00",
                "509.80",
                "510.75",
                12_345L,
                45_678L,
                "r"
            )
        ));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored)
            .isInstanceOf(UnusualWhalesOhlcDto.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRoundTripMarketTideDtoThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);

        UnusualWhalesMarketTideDto original = new UnusualWhalesMarketTideDto(List.of(
            new UnusualWhalesMarketTideDto.MarketTideItem(
                "2026-04-14T15:55:00Z",
                "2026-04-14",
                new BigDecimal("1250000.50"),
                new BigDecimal("980000.25"),
                42137
            )
        ));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored)
            .isInstanceOf(UnusualWhalesMarketTideDto.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRoundTripSpotGexLatestSnapshotResultThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);
        Instant snapshotAt = Instant.parse("2026-06-17T14:35:00Z");
        SpotGexSnapshotDto snapshot = new SpotGexSnapshotDto(
            "SPY",
            LocalDate.of(2026, 6, 17),
            decimal("550.0000"),
            snapshotAt,
            decimal("548.1200"),
            LocalDate.of(2026, 6, 17),
            snapshotAt,
            decimal("1.0000"),
            decimal("2.0000"),
            decimal("3.0000"),
            decimal("4.0000"),
            decimal("5.0000"),
            decimal("6.0000"),
            decimal("7.0000"),
            decimal("8.0000"),
            decimal("9.0000"),
            decimal("10.0000"),
            decimal("11.0000"),
            decimal("12.0000"),
            decimal("13.0000"),
            decimal("14.0000"),
            decimal("15.0000"),
            decimal("16.0000"),
            decimal("17.0000"),
            decimal("18.0000"),
            decimal("19.0000"),
            decimal("20.0000"),
            decimal("21.0000"),
            decimal("22.0000"),
            decimal("23.0000"),
            decimal("24.0000"),
            decimal("25.0000"),
            decimal("26.0000"),
            decimal("27.0000"),
            decimal("28.0000"),
            decimal("29.0000"),
            decimal("30.0000"),
            decimal("31.0000"),
            decimal("32.0000")
        );
        SpotGexQueryService.LatestSnapshotResult original =
            SpotGexQueryService.LatestSnapshotResult.stream(snapshotAt, List.of(snapshot));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(SpotGexQueryService.LatestSnapshotResult.class);
        assertThat((SpotGexQueryService.LatestSnapshotResult) restored)
            .usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRoundTripDarkPoolRecentResponseThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);

        DarkPoolRecentResponseDto original = new DarkPoolRecentResponseDto(List.of(
            new DarkPoolRecentTradeDto(
                "SPY",
                new BigDecimal("512.34"),
                1000,
                250000,
                "2026-04-14T15:56:00Z",
                new BigDecimal("512340.00"),
                new BigDecimal("512.35"),
                new BigDecimal("512.33"),
                100,
                120,
                "L",
                false,
                null,
                null,
                123456789L,
                null,
                "regular_settlement"
            )
        ));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored)
            .isInstanceOf(DarkPoolRecentResponseDto.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRoundTripFlowAlertResponseThroughRedisSerializer() {
        GenericJackson2JsonRedisSerializer serializer =
            ActiveActiveRedisCacheConfig.createRedisValueSerializer(objectMapper);

        UnusualWhalesFlowResponse original = new UnusualWhalesFlowResponse(
            List.of(new UnusualWhalesFlowAlertDto(
                "alert-1",
                "SPY",
                "SPY251103C00683000",
                new BigDecimal("683.00"),
                LocalDate.of(2025, 11, 3),
                "CALL",
                new BigDecimal("4.25"),
                new BigDecimal("4.20"),
                new BigDecimal("4.30"),
                new BigDecimal("512.34"),
                100,
                new BigDecimal("42500.00"),
                3,
                1250,
                900,
                new BigDecimal("1.39"),
                new BigDecimal("0.42"),
                new BigDecimal("0.45"),
                "RepeatedHits",
                true,
                false,
                true,
                false,
                true,
                "Technology",
                new BigDecimal("377.681157"),
                LocalDate.of(2025, 10, 28),
                1762174200L,
                1762174500L,
                new BigDecimal("30000.00"),
                new BigDecimal("12500.00"),
                Instant.parse("2025-11-03T14:30:00Z"),
                "rule-1",
                1,
                "postmarket",
                "Common Stock",
                false
            )),
            "cursor-newer",
            "cursor-older"
        );

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(UnusualWhalesFlowResponse.class);
        assertThat((UnusualWhalesFlowResponse) restored)
            .usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(original);
    }

    @Test
    void shouldRegisterCacheErrorHandlerWithSpringCaching() {
        ActiveActiveRedisCacheConfig config = new ActiveActiveRedisCacheConfig(null, objectMapper);

        assertThat(config).isInstanceOf(CachingConfigurer.class);
        assertThat(((CachingConfigurer) config).errorHandler()).isNotNull();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
