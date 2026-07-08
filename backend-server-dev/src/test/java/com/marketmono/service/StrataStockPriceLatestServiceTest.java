package com.marketmono.service;

import com.marketmono.dto.StrataStockPriceLatestResponse;
import com.marketmono.entity.StrataStockPrice;
import com.marketmono.repository.StrataStockPriceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrataStockPriceLatestServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:30:00Z");

    @Mock
    private StrataStockPriceRepository repository;

    private SimpleMeterRegistry meterRegistry;

    private StrataStockPriceLatestService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new StrataStockPriceLatestService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC),
            90,
            meterRegistry
        );
    }

    @Test
    void shouldReturnLatestRowsWithStaleMetadataAndMissingTickers() {
        when(repository.findLatestByDisplayTickers(List.of("SPXW", "SPY", "MSFT"))).thenReturn(List.of(
            row("SPXW", "SPX", NOW.minusSeconds(120)),
            row("SPY", "SPY", NOW.minusSeconds(30))
        ));

        StrataStockPriceLatestResponse response = service.getLatest(List.of("spxw", "SPY", "MSFT"));

        assertThat(response.prices()).hasSize(2);
        assertThat(response.prices())
            .filteredOn(price -> price.displayTicker().equals("SPXW"))
            .singleElement()
            .satisfies(price -> {
                assertThat(price.sourceTicker()).isEqualTo("SPX");
                assertThat(price.closePrice()).isEqualByComparingTo("7499.36");
                assertThat(price.changePercent()).isEqualByComparingTo("0.79");
                assertThat(price.stale()).isTrue();
                assertThat(price.ageSeconds()).isEqualTo(120);
            });
        assertThat(response.prices())
            .filteredOn(price -> price.displayTicker().equals("SPY"))
            .singleElement()
            .satisfies(price -> assertThat(price.stale()).isFalse());
        assertThat(response.missing())
            .singleElement()
            .satisfies(missing -> {
                assertThat(missing.displayTicker()).isEqualTo("MSFT");
                assertThat(missing.sourceTicker()).isEqualTo("MSFT");
                assertThat(missing.reason()).isEqualTo("not_persisted");
            });
        assertThat(meterRegistry.counter(
            "strata_stock_price.latest.stale",
            "source_ticker", "SPX",
            "result", "stale"
        ).count()).isEqualTo(1.0);
    }

    private static StrataStockPrice row(String displayTicker, String sourceTicker, Instant fetchedAt) {
        return StrataStockPrice.builder()
            .displayTicker(displayTicker)
            .sourceTicker(sourceTicker)
            .closePrice(new BigDecimal("7499.36"))
            .prevClosePrice(new BigDecimal("7440.43"))
            .changePercent(new BigDecimal("0.79"))
            .openPrice(new BigDecimal("7490.00"))
            .highPrice(new BigDecimal("7501.00"))
            .lowPrice(new BigDecimal("7488.00"))
            .volume(10_000L)
            .totalVolume(12_000L)
            .marketTime("market")
            .tapeTime(Instant.parse("2026-07-01T09:29:58Z"))
            .fetchedAt(fetchedAt)
            .rawResponse("{}")
            .build();
    }
}
