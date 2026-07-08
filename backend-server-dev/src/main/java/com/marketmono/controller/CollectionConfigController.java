package com.marketmono.controller;

import com.marketmono.dto.CollectionConfigAuditLogDto;
import com.marketmono.dto.CollectionConfigDto;
import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.dto.UpdateTickersRequest;
import com.marketmono.service.CollectionConfigService;
import com.marketmono.service.SpotGexQueryService;
import com.marketmono.util.HttpUtils;
import com.marketmono.util.PrincipalUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Admin controller for scheduled collection configuration.
 */
@RestController
@RequestMapping("/api/v1/admin/collection-config")
@RequiredArgsConstructor
@Slf4j
public class CollectionConfigController {

    private final CollectionConfigService collectionConfigService;
    private final SpotGexQueryService spotGexQueryService;

    @GetMapping("/spot-gex-tickers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollectionConfigDto> getSpotGexTickers() {
        log.debug("GET /api/v1/admin/collection-config/spot-gex-tickers");
        return ResponseEntity.ok(collectionConfigService.getSpotGexTickerConfig());
    }

    @PutMapping("/spot-gex-tickers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollectionConfigDto> updateSpotGexTickers(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateTickersRequest request,
            HttpServletRequest httpRequest) {
        UUID adminId = PrincipalUtils.requireAdminConfigActorId(userDetails);
        String ipAddress = HttpUtils.getClientIpWithRealIpFallback(httpRequest);

        log.info("PUT /api/v1/admin/collection-config/spot-gex-tickers - adminId: {}, tickerCount: {}, ip: {}",
            adminId, request.tickers().size(), ipAddress);

        return ResponseEntity.ok(collectionConfigService.updateSpotGexTickers(request.tickers(), adminId, ipAddress));
    }

    @GetMapping("/spot-gex-tickers/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CollectionConfigAuditLogDto>> getSpotGexTickerAuditLog(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        log.debug("GET /api/v1/admin/collection-config/spot-gex-tickers/audit-log - all={}", all);
        return ResponseEntity.ok(collectionConfigService.getSpotGexTickerAuditLog(all));
    }

    @GetMapping("/spot-gex-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SpotGexCollectionStatusDto>> getSpotGexCollectionStatus() {
        log.debug("GET /api/v1/admin/collection-config/spot-gex-status");
        return ResponseEntity.ok(spotGexQueryService.getCollectionStatus());
    }

}
