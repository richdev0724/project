package com.marketmono.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.DashboardContractSnapshotsResponse;
import com.marketmono.dto.OptionContractDto;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.entity.DashboardContractSnapshot;
import com.marketmono.repository.DashboardContractSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores and serves option contract screener snapshots for the dashboard
 * Top Contracts, Top ETF Orders, and Top Sweep Orders widgets.
 *
 * Read path: database-only from dashboard_contract_snapshots, always the
 * latest stored trading day (so on weekends Friday's data is shown). User
 * requests never call the upstream market data API for these widgets.
 *
 * Write path: the scheduler calls {@link #collectAndStoreSnapshots()} every
 * minute during the 9:30 AM - 4:00 PM ET window (Mon-Fri). Each widget is
 * fetched once per upstream order variant with the exact screener parameters
 * the frontend used to send, because the upstream order determines which
 * contracts make the top-200 cut. The frontend switches between the stored
 * variants locally, so filter changes never trigger API calls. Rows older
 * than the retention window are purged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardContractSnapshotService {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    // Collection window: 9:30 AM - 4:00 PM ET, Monday-Friday
    private static final LocalTime COLLECTION_WINDOW_OPEN = LocalTime.of(9, 30);
    private static final LocalTime COLLECTION_WINDOW_CLOSE = LocalTime.of(16, 0);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<OptionContractDto>> CONTRACT_LIST_TYPE = new TypeReference<>() {};

    /**
     * Screener parameters per widget, mirroring the exact query params each
     * dashboard component previously sent to the screener proxy. The order
     * param is added per variant.
     */
    private record WidgetConfig(Map<String, Object> baseParams, List<String> orders) {}

    private static final Map<String, WidgetConfig> WIDGETS = Map.of(
        "top-contracts", new WidgetConfig(
            Map.of(
                "is_otm", "true",
                "max_multileg_volume_ratio", "0.5",
                "min_premium", "1000000",
                "vol_greater_oi", "true",
                "limit", "200",
                "min_skew_perc", "0.65",
                "order_direction", "desc"
            ),
            List.of("premium", "volume", "stock_price")
        ),
        "top-etf-orders", new WidgetConfig(
            Map.of(
                "issue_types", "ETF",
                "is_otm", "true",
                "max_multileg_volume_ratio", "0.2",
                "min_premium", "100000",
                "vol_greater_oi", "true",
                "min_skew_perc", "0.65",
                "order_direction", "desc",
                "limit", "200"
            ),
            List.of("premium", "volume", "stock_price")
        ),
        "top-sweep-orders", new WidgetConfig(
            Map.of(
                "is_otm", "true",
                "max_multileg_volume_ratio", "0.2",
                "min_premium", "100000",
                "vol_greater_oi", "true",
                "min_skew_perc", "0.65",
                "min_sweep_volume_ratio", "0.55",
                "order_direction", "desc",
                "limit", "200"
            ),
            List.of("premium", "volume")
        )
    );

    private final DashboardContractSnapshotRepository dashboardContractSnapshotRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    @Value("${market-data.dashboard-contracts.retention-days:4}")
    private int retentionDays = 4;

    public static boolean isSupportedWidget(String widget) {
        return widget != null && WIDGETS.containsKey(widget);
    }

    /**
     * Serves the latest stored trading day's contract lists for a widget,
     * keyed by upstream order variant. No upstream fallback: when the store
     * is empty or stale, empty lists are returned.
     */
    public DashboardContractSnapshotsResponse getWidgetContractsFromDatabase(String widget) {
        WidgetConfig config = WIDGETS.get(widget);
        if (config == null) {
            throw new IllegalArgumentException("unsupported widget: " + widget);
        }

        try {
            LocalDate latestDate = dashboardContractSnapshotRepository
                .findLatestTradingDateByWidget(widget)
                .orElse(null);
            if (latestDate == null) {
                log.warn("[DASHBOARD-CONTRACTS] No snapshot available for widget {}", widget);
                return emptyResponse(widget, config);
            }

            List<DashboardContractSnapshot> snapshots =
                dashboardContractSnapshotRepository.findByWidgetAndTradingDate(widget, latestDate);

            Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            Map<String, List<OptionContractDto>> orders = new LinkedHashMap<>();
            Instant latestFetch = null;
            for (String order : config.orders()) {
                DashboardContractSnapshot snapshot = snapshots.stream()
                    .filter(s -> s.getSortOrder().equals(order))
                    .findFirst()
                    .orElse(null);
                if (snapshot == null || snapshot.getFetchedAt().isBefore(staleCutoff)) {
                    orders.put(order, List.of());
                    continue;
                }
                List<OptionContractDto> contracts = deserializeContracts(snapshot.getData());
                orders.put(order, contracts == null ? List.of() : contracts);
                if (latestFetch == null || snapshot.getFetchedAt().isAfter(latestFetch)) {
                    latestFetch = snapshot.getFetchedAt();
                }
            }

            return new DashboardContractSnapshotsResponse(
                widget,
                latestDate.toString(),
                latestFetch != null ? latestFetch.toString() : null,
                orders
            );
        } catch (Exception e) {
            // e.g. dashboard_contract_snapshots migration not yet applied
            log.warn("[DASHBOARD-CONTRACTS] Snapshot read failed for widget {} ({}), returning empty lists",
                widget, e.getMessage());
            return emptyResponse(widget, config);
        }
    }

    /**
     * Fetches every widget's contract lists (one upstream call per widget and
     * order variant) and replaces today's rows. Outside the market window it
     * only runs when the store is empty/stale so a fresh deploy still gets
     * data immediately.
     */
    public void collectAndStoreSnapshots() {
        ZonedDateTime marketNow = ZonedDateTime.now(NEW_YORK_ZONE);
        Instant staleCutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        if (!isWithinCollectionWindow(marketNow)
            && dashboardContractSnapshotRepository.existsByFetchedAtAfter(staleCutoff)) {
            log.debug("[DASHBOARD-CONTRACTS] Skipping snapshot collection outside market window");
            return;
        }

        LocalDate tradingDate = marketNow.toLocalDate();
        int stored = 0;
        int failed = 0;

        for (Map.Entry<String, WidgetConfig> entry : WIDGETS.entrySet()) {
            String widget = entry.getKey();
            WidgetConfig config = entry.getValue();

            for (String order : config.orders()) {
                Map<String, Object> params = new LinkedHashMap<>(config.baseParams());
                params.put("order", order);

                try {
                    OptionContractsResponse response = unusualWhalesClient.getOptionContractsUncached(params);
                    if (response == null || response.data() == null || response.data().isEmpty()) {
                        // Keep the previous snapshot instead of overwriting with an empty list.
                        failed++;
                        log.warn("[DASHBOARD-CONTRACTS] No data for widget {} order {}, keeping previous snapshot",
                            widget, order);
                        continue;
                    }

                    dashboardContractSnapshotRepository.upsert(
                        widget,
                        order,
                        tradingDate,
                        OBJECT_MAPPER.writeValueAsString(response.data()),
                        Instant.now()
                    );
                    stored++;
                } catch (Exception e) {
                    failed++;
                    log.warn("[DASHBOARD-CONTRACTS] Failed to store snapshot for widget {} order {}: {}",
                        widget, order, e.getMessage());
                }
            }
        }

        int purged = dashboardContractSnapshotRepository.deleteByTradingDateBefore(
            tradingDate.minusDays(retentionDays)
        );
        log.info("[DASHBOARD-CONTRACTS] Snapshot cycle finished - date: {}, stored: {}, failed: {}, purged: {}",
            tradingDate, stored, failed, purged);
    }

    private DashboardContractSnapshotsResponse emptyResponse(String widget, WidgetConfig config) {
        Map<String, List<OptionContractDto>> orders = new LinkedHashMap<>();
        for (String order : config.orders()) {
            orders.put(order, List.of());
        }
        return new DashboardContractSnapshotsResponse(widget, null, null, orders);
    }

    private boolean isWithinCollectionWindow(ZonedDateTime marketTime) {
        DayOfWeek day = marketTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = marketTime.toLocalTime();
        return !time.isBefore(COLLECTION_WINDOW_OPEN) && !time.isAfter(COLLECTION_WINDOW_CLOSE);
    }

    private List<OptionContractDto> deserializeContracts(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, CONTRACT_LIST_TYPE);
        } catch (Exception e) {
            log.warn("[DASHBOARD-CONTRACTS] Failed to deserialize stored contract list: {}", e.getMessage());
            return null;
        }
    }
}
