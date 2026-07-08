package com.marketmono.controller;

import com.marketmono.dto.PoliticianPortfoliosResponse;
import com.marketmono.dto.PoliticianTradesResponse;
import com.marketmono.dto.PoliticiansListResponse;
import com.marketmono.service.PartyEnrichmentService;
import com.marketmono.service.PoliticianService;
import com.marketmono.service.PoliticianSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for politician trades and portfolio data
 */
@RestController
@RequestMapping("/api/v1/politicians")
@RequiredArgsConstructor
@Slf4j
public class PoliticianController {
    
    private final PoliticianService politicianService;
    private final PoliticianSyncService syncService;
    private final PartyEnrichmentService partyEnrichmentService;
    
    /**
     * Manually trigger politician and trades sync
     * POST /api/v1/politicians/sync
     * 
     * @return Sync result message
     */
    @PostMapping("/sync")
    public ResponseEntity<String> syncPoliticians() {
        log.info("POST /api/v1/politicians/sync - Manual sync triggered");
        
        try {
            // Step 1: Extract and save politicians from portfolio-recent-trades
            var politicianResult = syncService.syncPoliticians();
            log.info("Politicians synced: fetched={}, saved={}", 
                politicianResult.fetchedCount(), politicianResult.savedCount());
            
            // Step 2: Sync portfolio trades (has politician_id)
            var portfolioResult = syncService.syncPortfolioTrades();
            log.info("Portfolio trades synced: fetched={}, saved={}", 
                portfolioResult.fetchedCount(), portfolioResult.savedCount());
            
            // Step 3: Sync congress trades (matches by name)
            var congressResult = syncService.syncCongressTrades();
            log.info("Congress trades synced: fetched={}, saved={}", 
                congressResult.fetchedCount(), congressResult.savedCount());
            
            // Step 4: Automatically enrich politicians with party data from Congress.gov
            int enrichedCount = 0;
            try {
                enrichedCount = partyEnrichmentService.enrichAllPoliticiansWithParty();
                log.info("✅ Automatic party enrichment completed - {} politicians updated", enrichedCount);
            } catch (Exception e) {
                log.error("⚠️ Party enrichment failed (non-fatal), continuing: {}", e.getMessage());
            }
            
            String message = String.format(
                "✅ Sync completed - Politicians: %d saved, Portfolio trades: %d saved, Congress trades: %d saved, Party enrichment: %d updated",
                politicianResult.savedCount(), portfolioResult.savedCount(), congressResult.savedCount(), enrichedCount
            );
            
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error syncing politicians", e);
            return ResponseEntity.internalServerError()
                .body("❌ Sync failed: " + e.getMessage());
        }
    }
    
    /**
     * Enrich politicians with party affiliation data from Congress.gov API
     * POST /api/v1/politicians/enrich-party
     * 
     * TODO: Disable this endpoint for production (internal use only)
     * With auto-creation implemented, enrichment happens automatically via lazy loading
     * This manual endpoint should only be available in dev/local environments
     * 
     * @return Enrichment result message with count of updated politicians
     */
    @PostMapping("/enrich-party")
    public ResponseEntity<String> enrichPoliticiansWithParty() {
        log.info("POST /api/v1/politicians/enrich-party - Party enrichment triggered");
        
        try {
            int enrichedCount = partyEnrichmentService.enrichAllPoliticiansWithParty();
            
            String message = String.format(
                "✅ Party enrichment completed - %d politicians updated with party affiliation data",
                enrichedCount
            );
            
            log.info(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Error enriching politicians with party data", e);
            return ResponseEntity.internalServerError()
                .body("❌ Party enrichment failed: " + e.getMessage());
        }
    }
    
    /**
     * Get all politicians (names and IDs)
     * GET /api/v1/politicians/list
     * 
     * @return List of all politicians
     */
    @GetMapping("/list")
    public ResponseEntity<PoliticiansListResponse> getPoliticiansList() {
        log.info("GET /api/v1/politicians/list");
        
        PoliticiansListResponse response = politicianService.getAllPoliticians();
        
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("No politicians data available");
            return ResponseEntity.noContent().build();
        }
        
        log.info("Returning {} politicians", response.data().size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get politician trades with optional filters
     * GET /api/v1/politicians/trades
     * 
     * @param limit Max trades (default 500, max 500)
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param ticker Filter by ticker
     * @param politicianId Filter by politician UUID
     * @param filterLateReports Only show late reports (>45 days)
     * @param page Page number
     * @return List of politician trades
     */
    @GetMapping("/trades")
    public ResponseEntity<PoliticianTradesResponse> getPoliticianTrades(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String ticker,
            @RequestParam(name = "politician_id", required = false) UUID politicianId,
            @RequestParam(name = "filter_late_reports", required = false) Boolean filterLateReports,
            @RequestParam(required = false) Integer page) {
        
        log.info("GET /api/v1/politicians/trades - limit={}, date={}, ticker={}, politicianId={}, filterLateReports={}, page={}", 
                 limit, date, ticker, politicianId, filterLateReports, page);
        
        PoliticianTradesResponse response = politicianService.getTrades(
            limit, date, ticker, politicianId, filterLateReports, page
        );
        
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("No politician trades available");
            return ResponseEntity.noContent().build();
        }
        
        log.info("Returning {} politician trades", response.data().size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get politician's portfolios and holdings
     * GET /api/v1/politicians/{politicianId}/portfolio
     * 
     * @param politicianId Politician's UUID
     * @param aggregateAllPortfolios Combine all portfolios into one
     * @return Politician's portfolios with holdings
     */
    @GetMapping("/{politicianId}/portfolio")
    public ResponseEntity<PoliticianPortfoliosResponse> getPoliticianPortfolio(
            @PathVariable UUID politicianId,
            @RequestParam(name = "aggregate_all_portfolios", required = false, defaultValue = "true") Boolean aggregateAllPortfolios) {
        
        log.info("GET /api/v1/politicians/{}/portfolio - aggregate={}", politicianId, aggregateAllPortfolios);
        
        PoliticianPortfoliosResponse response = politicianService.getPoliticianPortfolio(
            politicianId, aggregateAllPortfolios
        );
        
        if (response == null || response.data() == null || response.data().isEmpty()) {
            log.warn("No portfolio data for politician {}", politicianId);
            return ResponseEntity.noContent().build();
        }
        
        log.info("Returning {} portfolios for politician {}", response.data().size(), politicianId);
        return ResponseEntity.ok(response);
    }
}
