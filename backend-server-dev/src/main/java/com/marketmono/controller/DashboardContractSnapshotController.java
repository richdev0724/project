package com.marketmono.controller;

import com.marketmono.dto.DashboardContractSnapshotsResponse;
import com.marketmono.service.DashboardContractSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stored option contract screener snapshots for the dashboard
 * Top Contracts / Top ETF Orders / Top Sweep Orders widgets. Database-only:
 * user requests never reach the upstream market data API.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DashboardContractSnapshotController {

    private final DashboardContractSnapshotService dashboardContractSnapshotService;

    /**
     * GET /api/v1/dashboard/contract-snapshots/{widget}
     *
     * @param widget top-contracts, top-etf-orders, or top-sweep-orders
     * @return the latest stored trading day's contract lists per order variant
     */
    @GetMapping("/contract-snapshots/{widget}")
    public ResponseEntity<DashboardContractSnapshotsResponse> getContractSnapshots(
        @PathVariable String widget
    ) {
        if (!DashboardContractSnapshotService.isSupportedWidget(widget)) {
            log.warn("GET /api/v1/dashboard/contract-snapshots/{} - unsupported widget", widget);
            return ResponseEntity.badRequest().build();
        }

        log.debug("GET /api/v1/dashboard/contract-snapshots/{}", widget);
        return ResponseEntity.ok(dashboardContractSnapshotService.getWidgetContractsFromDatabase(widget));
    }
}
