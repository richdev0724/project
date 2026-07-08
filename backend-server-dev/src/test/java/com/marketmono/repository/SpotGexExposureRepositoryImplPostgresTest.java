package com.marketmono.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SpotGexExposureRepositoryImplPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
        .withDatabaseName("marketmono")
        .withUsername("postgres")
        .withPassword("postgres");

    private JdbcTemplate jdbcTemplate;
    private SpotGexExposureRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new SpotGexExposureRepositoryImpl(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS spot_gex_exposures");
        jdbcTemplate.execute("""
            CREATE TABLE spot_gex_exposures (
                ticker              VARCHAR(10)     NOT NULL,
                expiry              DATE            NOT NULL,
                strike              DECIMAL(12,4)   NOT NULL,
                collected_at        TIMESTAMPTZ     NOT NULL,
                price               DECIMAL(12,4),
                date                DATE,
                source_time         TIMESTAMPTZ,
                call_delta_oi       DECIMAL(24,4),
                call_gamma_oi       DECIMAL(24,4),
                call_vanna_oi       DECIMAL(24,4),
                call_charm_oi       DECIMAL(24,4),
                put_delta_oi        DECIMAL(24,4),
                put_gamma_oi        DECIMAL(24,4),
                put_vanna_oi        DECIMAL(24,4),
                put_charm_oi        DECIMAL(24,4),
                call_delta_vol      DECIMAL(24,4),
                call_gamma_vol      DECIMAL(24,4),
                call_vanna_vol      DECIMAL(24,4),
                call_charm_vol      DECIMAL(24,4),
                put_delta_vol       DECIMAL(24,4),
                put_gamma_vol       DECIMAL(24,4),
                put_vanna_vol       DECIMAL(24,4),
                put_charm_vol       DECIMAL(24,4),
                call_delta_bid      DECIMAL(24,4),
                call_delta_ask      DECIMAL(24,4),
                call_gamma_bid      DECIMAL(24,4),
                call_gamma_ask      DECIMAL(24,4),
                call_vanna_bid      DECIMAL(24,4),
                call_vanna_ask      DECIMAL(24,4),
                call_charm_bid      DECIMAL(24,4),
                call_charm_ask      DECIMAL(24,4),
                put_delta_bid       DECIMAL(24,4),
                put_delta_ask       DECIMAL(24,4),
                put_gamma_bid       DECIMAL(24,4),
                put_gamma_ask       DECIMAL(24,4),
                put_vanna_bid       DECIMAL(24,4),
                put_vanna_ask       DECIMAL(24,4),
                put_charm_bid       DECIMAL(24,4),
                put_charm_ask       DECIMAL(24,4),
                PRIMARY KEY (ticker, expiry, strike, collected_at)
            )
            """);
    }

    @Test
    void shouldQueryHistoryOnPlainPostgresWithoutTimescaleFunctions() {
        LocalDate expiry = LocalDate.of(2026, 4, 17);
        BigDecimal strike = new BigDecimal("500.0000");
        insertExposure("SPY", expiry, strike, Instant.parse("2026-04-05T14:31:00Z"), "9.5000", "-3.2500");
        insertExposure("SPY", expiry, strike, Instant.parse("2026-04-06T14:36:00Z"), "15.7500", "-4.7500");

        List<SpotGexExposureRepository.SpotGexHistoryProjection> history = repository.findHistory(
            "SPY",
            Instant.parse("2026-04-05T14:30:00Z"),
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

    @Test
    void shouldQueryCollectionStatusOnlyForRequestedTickers() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate expiry = today.plusDays(14);
        Instant firstCycle = today.atTime(14, 31).toInstant(ZoneOffset.UTC);
        Instant secondCycle = firstCycle.plusSeconds(60);

        insertExposure("SPY", expiry, new BigDecimal("500.0000"), firstCycle, "9.5000", "-3.2500");
        insertExposure("SPY", expiry, new BigDecimal("501.0000"), firstCycle, "10.5000", "-4.2500");
        insertExposure("SPY", expiry, new BigDecimal("500.0000"), secondCycle, "11.5000", "-5.2500");
        insertExposure("AAPL", expiry, new BigDecimal("200.0000"), secondCycle, "12.5000", "-6.2500");

        List<SpotGexExposureRepository.SpotGexStatusProjection> status =
            repository.findCollectionStatus(List.of("SPY", "QQQ"));

        assertThat(status).extracting(SpotGexExposureRepository.SpotGexStatusProjection::getTicker)
            .containsExactly("QQQ", "SPY");
        assertThat(status.get(0).getLastCollectedAt()).isNull();
        assertThat(status.get(0).getRowsToday()).isZero();
        assertThat(status.get(0).getCollectionCycles()).isZero();
        assertThat(status.get(1).getLastCollectedAt()).isEqualTo(secondCycle);
        assertThat(status.get(1).getRowsToday()).isEqualTo(3);
        assertThat(status.get(1).getCollectionCycles()).isEqualTo(2);
    }

    private void insertExposure(
        String ticker,
        LocalDate expiry,
        BigDecimal strike,
        Instant collectedAt,
        String callGammaOi,
        String putGammaOi
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO spot_gex_exposures (
                    ticker, expiry, strike, collected_at, price, date, source_time,
                    call_gamma_oi, put_gamma_oi,
                    call_gamma_vol, put_gamma_vol,
                    call_gamma_bid, call_gamma_ask, put_gamma_bid, put_gamma_ask
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            ticker,
            expiry,
            strike,
            Timestamp.from(collectedAt),
            new BigDecimal("502.1200"),
            expiry.minusDays(1),
            Timestamp.from(collectedAt.minusSeconds(2)),
            new BigDecimal(callGammaOi),
            new BigDecimal(putGammaOi),
            new BigDecimal("100.0000"),
            new BigDecimal("200.0000"),
            new BigDecimal("1.2500"),
            new BigDecimal("2.5000"),
            new BigDecimal("-1.7500"),
            new BigDecimal("-2.0000")
        );
    }
}
