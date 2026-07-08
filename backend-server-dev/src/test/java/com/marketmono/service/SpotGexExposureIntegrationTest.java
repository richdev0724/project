package com.marketmono.service;

import com.marketmono.entity.SpotGexExposure;
import com.marketmono.repository.SpotGexExposureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.kafka.enabled=false",
    "spring.main.lazy-initialization=false",
    "spot-gex.collection.enabled=false",
    "unusual-whales.api.api-key=test-api-key",
    "unusual-whales.websocket.enabled=false",
    "unusual-whales.flow.sync.enabled=false",
    "politician.sync.enabled=false",
    "market-data.stock-prices.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SpotGexExposureIntegrationTest {

    private static final DockerImageName TIMESCALE_IMAGE = DockerImageName.parse("timescale/timescaledb:latest-pg17")
        .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TIMESCALE_IMAGE)
        .withDatabaseName("marketmono")
        .withUsername("postgres")
        .withPassword("postgres");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpotGexExposureBatchPersistence batchPersistence;

    @Autowired
    private SpotGexExposureRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE spot_gex_exposures");
    }

    @Test
    void shouldApplySpotGexLiquibaseMigrationAsTimescaleHypertable() {
        Integer hypertableCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM timescaledb_information.hypertables
                WHERE hypertable_name = 'spot_gex_exposures'
                """,
            Integer.class
        );

        Boolean compressionEnabled = jdbcTemplate.queryForObject(
            """
                SELECT compression_enabled
                FROM timescaledb_information.hypertables
                WHERE hypertable_name = 'spot_gex_exposures'
                """,
            Boolean.class
        );

        Integer supportingIndexCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'spot_gex_exposures'
                  AND indexname IN (
                    'idx_spot_gex_exposures_ticker_expiry_collected',
                    'idx_spot_gex_exposures_ticker_expiry_strike_collected_desc',
                    'idx_spot_gex_exposures_ticker_collected_desc'
                  )
                """,
            Integer.class
        );

        assertThat(hypertableCount).isEqualTo(1);
        assertThat(compressionEnabled).isTrue();
        assertThat(supportingIndexCount).isEqualTo(3);
    }

    @Test
    void shouldPersistBatchesSkipDuplicatesAndQueryHistoryBuckets() {
        LocalDate expiry = LocalDate.of(2026, 4, 17);
        BigDecimal strike = new BigDecimal("500.0000");
        Instant firstCollectedAt = Instant.parse("2026-04-06T14:31:00Z");
        Instant secondCollectedAt = Instant.parse("2026-04-06T14:36:00Z");

        SpotGexExposure firstSnapshot = sampleExposure(
            "SPY",
            expiry,
            strike,
            firstCollectedAt,
            new BigDecimal("12.5000"),
            new BigDecimal("-5.2500")
        );
        SpotGexExposure duplicateSnapshot = sampleExposure(
            "SPY",
            expiry,
            strike,
            firstCollectedAt,
            new BigDecimal("12.5000"),
            new BigDecimal("-5.2500")
        );
        SpotGexExposure secondSnapshot = sampleExposure(
            "SPY",
            expiry,
            strike,
            secondCollectedAt,
            new BigDecimal("15.7500"),
            new BigDecimal("-4.7500")
        );

        SpotGexExposureBatchPersistence.BatchPersistResult firstResult =
            batchPersistence.persistBatch(List.of(firstSnapshot, duplicateSnapshot));
        SpotGexExposureBatchPersistence.BatchPersistResult secondResult =
            batchPersistence.persistBatch(List.of(secondSnapshot));

        assertThat(firstResult.attempted()).isEqualTo(2);
        assertThat(firstResult.inserted()).isEqualTo(1);
        assertThat(firstResult.skipped()).isEqualTo(1);
        assertThat(secondResult.attempted()).isEqualTo(1);
        assertThat(secondResult.inserted()).isEqualTo(1);
        assertThat(secondResult.skipped()).isZero();

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findLatestCollectedAtByTicker("SPY")).contains(secondCollectedAt);

        List<SpotGexExposureRepository.SpotGexHistoryProjection> history = repository.findHistory(
            "SPY",
            firstCollectedAt.minusSeconds(60),
            "5 minutes",
            expiry,
            strike
        );

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSnapshotAt()).isEqualTo(Instant.parse("2026-04-06T14:35:00Z"));
        assertThat(history.get(0).getCallGammaOi()).isEqualByComparingTo("15.7500");
        assertThat(history.get(0).getPutGammaOi()).isEqualByComparingTo("-4.7500");
        assertThat(history.get(1).getSnapshotAt()).isEqualTo(Instant.parse("2026-04-06T14:30:00Z"));
        assertThat(history.get(1).getCallGammaOi()).isEqualByComparingTo("12.5000");
        assertThat(history.get(1).getPutGammaOi()).isEqualByComparingTo("-5.2500");
    }

    @Test
    void shouldQueryHistoryWithoutExpiryOrStrikeAcrossPreviousDays() {
        LocalDate expiry = LocalDate.of(2026, 4, 17);
        BigDecimal strike = new BigDecimal("500.0000");
        Instant previousDayCollectedAt = Instant.parse("2026-04-05T14:31:00Z");
        Instant currentDayCollectedAt = Instant.parse("2026-04-06T14:36:00Z");

        batchPersistence.persistBatch(List.of(
            sampleExposure(
                "SPY",
                expiry,
                strike,
                previousDayCollectedAt,
                new BigDecimal("9.5000"),
                new BigDecimal("-3.2500")
            ),
            sampleExposure(
                "SPY",
                expiry,
                strike,
                currentDayCollectedAt,
                new BigDecimal("15.7500"),
                new BigDecimal("-4.7500")
            )
        ));

        List<SpotGexExposureRepository.SpotGexHistoryProjection> history = repository.findHistory(
            "SPY",
            previousDayCollectedAt.minusSeconds(60),
            "5 minutes",
            null,
            null
        );

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getSnapshotAt()).isEqualTo(Instant.parse("2026-04-06T14:35:00Z"));
        assertThat(history.get(0).getCallGammaOi()).isEqualByComparingTo("15.7500");
        assertThat(history.get(1).getSnapshotAt()).isEqualTo(Instant.parse("2026-04-05T14:30:00Z"));
        assertThat(history.get(1).getCallGammaOi()).isEqualByComparingTo("9.5000");
    }

    private SpotGexExposure sampleExposure(
        String ticker,
        LocalDate expiry,
        BigDecimal strike,
        Instant collectedAt,
        BigDecimal callGammaOi,
        BigDecimal putGammaOi
    ) {
        return SpotGexExposure.builder()
            .ticker(ticker)
            .expiry(expiry)
            .strike(strike)
            .collectedAt(collectedAt)
            .price(new BigDecimal("502.1200"))
            .marketDate(LocalDate.of(2026, 4, 6))
            .sourceTime(collectedAt.minusSeconds(2))
            .callDeltaOi(new BigDecimal("42.2500"))
            .callGammaOi(callGammaOi)
            .callVannaOi(new BigDecimal("8.1000"))
            .callCharmOi(new BigDecimal("2.5000"))
            .putDeltaOi(new BigDecimal("-38.5000"))
            .putGammaOi(putGammaOi)
            .putVannaOi(new BigDecimal("-7.9000"))
            .putCharmOi(new BigDecimal("-1.7500"))
            .build();
    }
}
