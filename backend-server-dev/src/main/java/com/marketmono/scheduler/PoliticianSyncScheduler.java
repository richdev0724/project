package com.marketmono.scheduler;

import com.marketmono.dto.SyncResult;
import com.marketmono.service.PartyEnrichmentService;
import com.marketmono.service.PoliticianSyncService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to sync politician data from upstream market data API
 * Runs daily to keep local database updated
 * Includes automatic party enrichment from Congress.gov API
 */
@Component
public class PoliticianSyncScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(PoliticianSyncScheduler.class);
    
    private final PoliticianSyncService syncService;
    private final PartyEnrichmentService partyEnrichmentService;
    
    public PoliticianSyncScheduler(PoliticianSyncService syncService, 
                                   PartyEnrichmentService partyEnrichmentService) {
        this.syncService = syncService;
        this.partyEnrichmentService = partyEnrichmentService;
    }
    
    /**
     * Sync politicians and trades
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "politicians_syncAll", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void syncPoliticians() {
        logger.info("Starting scheduled politician sync...");
        
        try {
            // Step 1: Sync politicians list
            SyncResult politicianResult = syncService.syncPoliticians();
            logger.info("Politicians synced: fetched={}, saved={}", 
                politicianResult.fetchedCount(), politicianResult.savedCount());
            
            // Step 2: Sync portfolio trades
            SyncResult portfolioResult = syncService.syncPortfolioTrades();
            logger.info("Portfolio trades synced: fetched={}, saved={}", 
                portfolioResult.fetchedCount(), portfolioResult.savedCount());
            
            // Step 3: Sync congress trades
            SyncResult congressResult = syncService.syncCongressTrades();
            logger.info("Congress trades synced: fetched={}, saved={}", 
                congressResult.fetchedCount(), congressResult.savedCount());
                
            // Step 4: Cleanup old trades
            syncService.cleanupOldTrades();
            
            // Step 5: Enrich politicians with party data from Congress.gov API
            try {
                int enrichedCount = partyEnrichmentService.enrichAllPoliticiansWithParty();
                logger.info("Party enrichment completed: {} politicians updated", enrichedCount);
            } catch (Exception e) {
                logger.error("Party enrichment failed (non-fatal): {}", e.getMessage());
            }
            
            logger.info("Scheduled politician sync completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during scheduled politician sync: {}", e.getMessage(), e);
        }
    }
}
