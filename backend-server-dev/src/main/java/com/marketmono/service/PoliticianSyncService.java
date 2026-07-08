package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.PoliticianSyncConfig;
import com.marketmono.dto.CongressTrade;
import com.marketmono.dto.CongressTradesResponse;
import com.marketmono.dto.PoliticianTradeDto;
import com.marketmono.dto.PoliticianTradesResponse;
import com.marketmono.dto.SyncResult;
import com.marketmono.entity.IssuerType;
import com.marketmono.entity.MemberType;
import com.marketmono.entity.Politician;
import com.marketmono.entity.PoliticianTrade;
import com.marketmono.entity.TransactionType;
import com.marketmono.repository.PoliticianRepository;
import com.marketmono.repository.PoliticianTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for scheduled syncing of politician trades
 * Handles both Congress API and Portfolio API sources
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoliticianSyncService {
    
    private final UnusualWhalesClient uwClient;
    private final PoliticianTradeRepository repository;
    private final PoliticianRepository politicianRepository;
    private final PoliticianSyncConfig config;
    
    /**
     * Sync politicians from upstream market data API (/api/politician-portfolios/recent_trades)
     * Extracts unique politicians from Portfolio trades and saves them to the database
     * This must be run BEFORE syncing Congress trades so they can be matched to politicians
     */
    @Transactional
    public SyncResult syncPoliticians() {
        try {
            log.info("[SYNC] Starting Politicians sync from Portfolio API");
            
            int totalFetched = 0;
            int totalSaved = 0;
            int totalSkipped = 0;
            int page = 1;
            
            List<Politician> politicians = new ArrayList<>();
            
            while (page <= config.getMaxPagesPerSync()) {
                log.debug("[SYNC] Fetching page {} (limit=200)", page);
                
                var response = uwClient.getPoliticianTrades(200, null, null, null, null, page);
                
                if (response == null || response.data() == null || response.data().isEmpty()) {
                    log.debug("[SYNC] Page {} returned empty, ending pagination", page);
                    break;
                }
                
                int fetched = response.data().size();
                totalFetched += fetched;
                log.info("[POLITICIANS] Page {} returned {} trades with politician info", page, fetched);
                
                int pageSaved = 0;
                int pageSkipped = fetched;
                
                for (var portfolioTrade : response.data()) {
                    var politician = isValidPolitician(portfolioTrade);
                    if (politician.isPresent()) {
                        politicians.add(politician.get());
                        pageSaved++;
                        pageSkipped--;
                        
                        // Batch insert
                        if (politicians.size() >= 100) {
                            politicianRepository.saveAll(politicians);
                            politicians.clear();
                        }
                    }
                }
                
                totalSaved += pageSaved;
                totalSkipped += pageSkipped;
                
                // Stop if this was a partial page (less than limit means no more data)
                if (fetched < 200) {
                    log.info("[SYNC] Page {} was partial ({}), no more pages available", page, fetched);
                    break;
                }
                
                page++;
            }
            
            // Save remaining
            if (!politicians.isEmpty()) {
                politicianRepository.saveAll(politicians);
            }
            
            log.info("[POLITICIANS] Fetched {} across {} pages - synced {} (skipped {})", 
                totalFetched, page - 1, totalSaved, totalSkipped);
            return SyncResult.success(totalFetched, totalSaved, totalSkipped);
            
        } catch (Exception e) {
            log.error("[SYNC] Politicians sync failed: {}", e.getMessage(), e);
            return SyncResult.error("Politicians sync failed: " + e.getMessage());
        }
    }
    
    /**
     * Sync trades from Congress API (/api/congress/recent-trades)
     */
    @Transactional
    public SyncResult syncCongressTrades() {
        if (!config.getCongressApi().isEnabled()) {
            return SyncResult.skipped("Congress API sync disabled in config");
        }
        
        try {
            log.info("[SYNC] Starting Congress API sync");
            CongressTradesResponse response = uwClient.getCongressTrades();
            
            if (response == null || response.data() == null) {
                log.warn("[SYNC] Congress API returned empty response");
                return SyncResult.success(0, 0, 0);
            }
            
            int fetched = response.data().size();
            log.info("[CONGRESS] API returned {} trades", fetched);
            
            if (fetched == 0) {
                return SyncResult.success(0, 0, 0);
            }
            
            int saved = 0;
            int skipped = fetched;
            
            List<PoliticianTrade> trades = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            
            for (var congressTrade : response.data()) {
                var validTrade = processAndValidateCongressTrade(congressTrade, seenKeys);
                if (validTrade.isPresent()) {
                    trades.add(validTrade.get());
                    saved++;
                    skipped--;
                    
                    // Batch insert
                    if (trades.size() >= config.getCongressApi().getBatchSize()) {
                        repository.saveAll(trades);
                        trades.clear();
                        // Keep tracking keys across batches within same transaction
                    }
                }
            }
            
            // Save remaining
            if (!trades.isEmpty()) {
                repository.saveAll(trades);
            }
            
            log.info("[CONGRESS] fetched {} - synced {} (skipped {})", fetched, saved, skipped);
            return SyncResult.success(fetched, saved, skipped);
            
        } catch (Exception e) {
            log.error("[SYNC] Congress API sync failed: {}", e.getMessage(), e);
            return SyncResult.error("Congress API sync failed: " + e.getMessage());
        }
    }
    
    /**
     * Sync trades from Portfolio API (/api/politician-portfolios/recent_trades)
     */
    @Transactional
    public SyncResult syncPortfolioTrades() {
        if (!config.getPortfolioApi().isEnabled()) {
            return SyncResult.skipped("Portfolio API sync disabled in config");
        }
        
        try {
            log.info("[SYNC] Starting Portfolio API sync");
            
            int totalFetched = 0;
            int totalSaved = 0;
            int totalSkipped = 0;
            int page = 1;
            
            List<PoliticianTrade> trades = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            
            while (page <= config.getMaxPagesPerSync()) {
                log.debug("[SYNC] Fetching portfolio trades page {} (limit=200)", page);
                
                PoliticianTradesResponse response = uwClient.getPoliticianTrades(200, null, null, null, null, page);
                
                if (response == null || response.data() == null || response.data().isEmpty()) {
                    log.debug("[SYNC] Page {} returned empty, ending pagination", page);
                    break;
                }
                
                int fetched = response.data().size();
                totalFetched += fetched;
                log.info("[PORTFOLIO] Page {} returned {} trades", page, fetched);
                
                int pageSaved = 0;
                int pageSkipped = fetched;
                
                for (var portfolioTrade : response.data()) {
                    // Generate unique key for this trade
                    UUID politicianId = portfolioTrade.politicianId();
                    if (politicianId != null) {
                        String uniqueKey = String.format("PORTFOLIO:%s:%s:%s",
                            politicianId,
                            portfolioTrade.ticker() != null ? portfolioTrade.ticker() : "NULL",
                            portfolioTrade.transactionDate()
                        );
                        
                        // Skip if already seen in this batch
                        if (seenKeys.contains(uniqueKey)) {
                            log.debug("[PORTFOLIO] Skipping duplicate within batch: {}", uniqueKey);
                            continue;
                        }
                        seenKeys.add(uniqueKey);
                    }
                    
                    var validTrade = isValidPortfolioTrade(portfolioTrade);
                    if (validTrade.isPresent()) {
                        trades.add(validTrade.get());
                        pageSaved++;
                        pageSkipped--;
                        
                        // Batch insert
                        if (trades.size() >= config.getPortfolioApi().getBatchSize()) {
                            repository.saveAll(trades);
                            trades.clear();
                        }
                    }
                }
                
                totalSaved += pageSaved;
                totalSkipped += pageSkipped;
                
                // Stop if this was a partial page (less than limit means no more data)
                if (fetched < 200) {
                    log.info("[SYNC] Page {} was partial ({}), no more pages available", page, fetched);
                    break;
                }
                
                page++;
            }
            
            // Save remaining
            if (!trades.isEmpty()) {
                repository.saveAll(trades);
            }
            
            log.info("[PORTFOLIO] Fetched {} across {} pages - synced {} (skipped {})", 
                totalFetched, page - 1, totalSaved, totalSkipped);
            return SyncResult.success(totalFetched, totalSaved, totalSkipped);
            
        } catch (Exception e) {
            log.error("[SYNC] Portfolio API sync failed: {}", e.getMessage(), e);
            return SyncResult.error("Portfolio API sync failed: " + e.getMessage());
        }
    }
    
    /**
     * Cleanup old trades based on retention days config
     */
    @Transactional
    public void cleanupOldTrades() {
        int retentionDays = Math.max(config.getRetentionDays(), 30); // Min 30 days
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        
        int deleted = repository.deleteOldTrades(cutoffDate);
        if (deleted > 0) {
            log.info("[CLEANUP] Deleted {} trades older than {} days", deleted, retentionDays);
        }
    }
    
    /**
     * Process and validate Congress trade with error handling
     */
    private java.util.Optional<PoliticianTrade> processAndValidateCongressTrade(CongressTrade congressTrade, java.util.Set<String> seenKeys) {
        try {
            return isValidCongressTrade(congressTrade, seenKeys);
        } catch (Exception e) {
            log.warn("[CONGRESS] Error processing trade for {}: {}", congressTrade.name(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Validate and map Congress trade to entity if valid
     */
    private java.util.Optional<PoliticianTrade> isValidCongressTrade(CongressTrade congressTrade, java.util.Set<String> seenKeys) {
        try {
            // Look up politician by name since API doesn't provide politician_id
            if (congressTrade.name() == null || congressTrade.name().isBlank()) {
                log.debug("[SYNC] Skipping Congress trade - missing politician name");
                return java.util.Optional.empty();
            }
            
            var politician = politicianRepository.findByName(congressTrade.name());
            if (politician.isEmpty()) {
                log.warn("[CONGRESS] Trade skipped - politician '{}' not found in DB (make sure to sync politicians first)", congressTrade.name());
                return java.util.Optional.empty();
            }
            
            UUID politicianId = politician.get().getId();
            
            // Generate unique key for duplicate check (handles NULL tickers)
            String uniqueKey = String.format("CONGRESS:%s:%s:%s",
                politicianId,
                congressTrade.ticker() != null ? congressTrade.ticker() : "NULL",
                congressTrade.transactionDate()
            );
            
            log.debug("[CONGRESS] Checking duplicate for uniqueKey: {}", uniqueKey);
            
            // Check if already seen in this batch
            if (seenKeys.contains(uniqueKey)) {
                log.debug("[CONGRESS] Skipping duplicate within batch: {}", uniqueKey);
                return java.util.Optional.empty();
            }
            
            // Check for duplicate in database
            if (repository.existsByUniqueKey(uniqueKey)) {
                log.debug("[CONGRESS] Skipping duplicate in database: {}", uniqueKey);
                return java.util.Optional.empty();
            }
            
            // Mark as seen
            seenKeys.add(uniqueKey);
            log.debug("[CONGRESS] Trade is new, will insert: {}", uniqueKey);
            
            // Map to entity with enum conversions
            PoliticianTrade trade = PoliticianTrade.builder()
                .politicianId(politicianId)
                .ticker(congressTrade.ticker())
                .transactionDate(congressTrade.transactionDate())
                .filedAtDate(congressTrade.filedAtDate())
                .txnType(TransactionType.fromString(congressTrade.txnType()))
                .amounts(congressTrade.amounts())
                .issuer(IssuerType.fromString(congressTrade.issuer()))
                .memberType(MemberType.fromString(congressTrade.memberType()))
                .reporter(congressTrade.reporter())
                .isActive(true)
                .notes(congressTrade.notes())
                .source("CONGRESS")
                .uniqueKey(uniqueKey)
                .syncedAt(LocalDateTime.now())
                .build();
            
            return java.util.Optional.of(trade);
        } catch (Exception e) {
            log.warn("[CONGRESS] Error validating trade for {}: {}", congressTrade.name(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Validate and map Portfolio trade to entity if valid
     */
    private java.util.Optional<PoliticianTrade> isValidPortfolioTrade(PoliticianTradeDto portfolioTrade) {
        UUID politicianId = portfolioTrade.politicianId();
        
        // Validate required fields
        if (politicianId == null) {
            log.debug("[SYNC] Skipping Portfolio trade - missing politician_id: {}", portfolioTrade.name());
            return java.util.Optional.empty();
        }
        
        // Generate unique key for duplicate check (handles NULL tickers)
        String uniqueKey = String.format("PORTFOLIO:%s:%s:%s",
            politicianId,
            portfolioTrade.ticker() != null ? portfolioTrade.ticker() : "NULL",
            portfolioTrade.transactionDate()
        );
        
        // Check for duplicate using unique key
        if (repository.existsByUniqueKey(uniqueKey)) {
            log.debug("[PORTFOLIO] Skipping duplicate trade: {}", uniqueKey);
            return java.util.Optional.empty();
        }
        
        // Map to entity with enum conversions
        PoliticianTrade trade = PoliticianTrade.builder()
            .politicianId(politicianId)
            .ticker(portfolioTrade.ticker())
            .transactionDate(portfolioTrade.transactionDate())
            .filedAtDate(portfolioTrade.filedAtDate())
            .txnType(TransactionType.fromString(portfolioTrade.txnType()))
            .amounts(portfolioTrade.amounts())
            .issuer(IssuerType.fromString(portfolioTrade.issuer()))
            .memberType(MemberType.fromString(portfolioTrade.memberType()))
            .reporter(portfolioTrade.reporter())
            .isActive(true)
            .notes(portfolioTrade.notes())
            .source("PORTFOLIO")
            .uniqueKey(uniqueKey)
            .syncedAt(LocalDateTime.now())
            .build();
        
        return java.util.Optional.of(trade);
    }
    
    /**
     * Validate if portfolio trade contains valid politician info for storage
     */
    private java.util.Optional<Politician> isValidPolitician(PoliticianTradeDto portfolioTrade) {
        try {
            var politicianId = portfolioTrade.politicianId();
            
            // Skip if missing ID
            if (politicianId == null) {
                return java.util.Optional.empty();
            }
            
            // Check if politician already exists
            if (politicianRepository.existsById(politicianId)) {
                return java.util.Optional.empty();
            }
            
            // Create new politician from portfolio trade with enum conversion
            // Party is set to "UNK" (Unknown) since politician-portfolios endpoint doesn't provide it
            Politician politician = Politician.builder()
                .id(politicianId)
                .name(portfolioTrade.name())
                .isActive(true)
                .memberType(MemberType.fromString(portfolioTrade.memberType()))
                .party("UNK")  // Default to Unknown - party data comes from Congress API
                .build();
            
            return java.util.Optional.of(politician);
        } catch (Exception e) {
            log.warn("[SYNC] Error validating politician from trade: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }
}