package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DashboardContractSnapshotsResponse;
import com.marketmono.entity.DashboardContractSnapshot;
import com.marketmono.repository.DashboardContractSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardContractSnapshotServiceTest {

    private static final String CONTRACT_JSON = """
        [{
            "option_symbol": "SPY251219C00650000",
            "ticker_symbol": "SPY",
            "premium": 2500000.75,
            "volume": 18000,
            "open_interest": 9000,
            "ask_side_volume": 12000,
            "bid_side_volume": 4000,
            "mid_volume": 1500,
            "no_side_volume": 500,
            "issue_type": "ETF",
            "close": "3.25",
            "avg_price": "3.10"
        }]
        """;

    @Mock
    private DashboardContractSnapshotRepository dashboardContractSnapshotRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private DashboardContractSnapshotService dashboardContractSnapshotService;

    @Test
    void shouldServeLatestTradingDayFromDatabaseWithoutUpstreamCalls() {
        LocalDate friday = LocalDate.of(2026, 7, 3);
        when(dashboardContractSnapshotRepository.findLatestTradingDateByWidget("top-etf-orders"))
            .thenReturn(Optional.of(friday));
        when(dashboardContractSnapshotRepository.findByWidgetAndTradingDate("top-etf-orders", friday))
            .thenReturn(List.of(
                snapshot("top-etf-orders", "premium", friday),
                snapshot("top-etf-orders", "volume", friday),
                snapshot("top-etf-orders", "stock_price", friday)
            ));

        DashboardContractSnapshotsResponse response =
            dashboardContractSnapshotService.getWidgetContractsFromDatabase("top-etf-orders");

        assertThat(response.widget()).isEqualTo("top-etf-orders");
        assertThat(response.tradingDate()).isEqualTo("2026-07-03");
        assertThat(response.orders()).containsOnlyKeys("premium", "volume", "stock_price");
        assertThat(response.orders().get("premium")).hasSize(1);
        assertThat(response.orders().get("premium").get(0).optionSymbol()).isEqualTo("SPY251219C00650000");
        assertThat(response.orders().get("premium").get(0).issueType()).isEqualTo("ETF");
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldReturnEmptyListsWithoutUpstreamFallbackWhenSnapshotsMissing() {
        when(dashboardContractSnapshotRepository.findLatestTradingDateByWidget("top-contracts"))
            .thenReturn(Optional.empty());

        DashboardContractSnapshotsResponse response =
            dashboardContractSnapshotService.getWidgetContractsFromDatabase("top-contracts");

        assertThat(response.tradingDate()).isNull();
        assertThat(response.orders()).containsOnlyKeys("premium", "volume", "stock_price");
        assertThat(response.orders().values()).allSatisfy(list -> assertThat(list).isEmpty());
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldTreatStaleSnapshotsAsMissing() {
        LocalDate oldDate = LocalDate.now().minusDays(10);
        DashboardContractSnapshot stale = snapshot("top-sweep-orders", "premium", oldDate);
        stale.setFetchedAt(Instant.now().minus(Duration.ofDays(10)));

        when(dashboardContractSnapshotRepository.findLatestTradingDateByWidget("top-sweep-orders"))
            .thenReturn(Optional.of(oldDate));
        when(dashboardContractSnapshotRepository.findByWidgetAndTradingDate("top-sweep-orders", oldDate))
            .thenReturn(List.of(stale));

        DashboardContractSnapshotsResponse response =
            dashboardContractSnapshotService.getWidgetContractsFromDatabase("top-sweep-orders");

        assertThat(response.orders()).containsOnlyKeys("premium", "volume");
        assertThat(response.orders().values()).allSatisfy(list -> assertThat(list).isEmpty());
        verifyNoInteractions(unusualWhalesClient);
    }

    @Test
    void shouldRejectUnsupportedWidget() {
        assertThat(DashboardContractSnapshotService.isSupportedWidget("top-contracts")).isTrue();
        assertThat(DashboardContractSnapshotService.isSupportedWidget("nope")).isFalse();
        assertThatThrownBy(() -> dashboardContractSnapshotService.getWidgetContractsFromDatabase("nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private DashboardContractSnapshot snapshot(String widget, String order, LocalDate tradingDate) {
        return DashboardContractSnapshot.builder()
            .widget(widget)
            .sortOrder(order)
            .tradingDate(tradingDate)
            .data(CONTRACT_JSON)
            .fetchedAt(Instant.now())
            .build();
    }
}
