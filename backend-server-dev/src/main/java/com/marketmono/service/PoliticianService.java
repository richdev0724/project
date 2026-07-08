package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.*;
import com.marketmono.entity.MemberType;
import com.marketmono.entity.Politician;
import com.marketmono.repository.PoliticianRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for politician trades and portfolio data
 * Fetches data from upstream market data API and provides business logic layer
 * Automatically enriches with party data from Congress.gov API as needed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoliticianService {
    
    private final UnusualWhalesClient uwClient;
    private final PoliticianRepository politicianRepository;
    private final PartyEnrichmentService partyEnrichmentService;
    
    /**
     * Get all politicians from database (includes party affiliation)
     * 
     * @return List of all politicians with names, IDs, and party data
     */
    public PoliticiansListResponse getAllPoliticians() {
        log.debug("Fetching all politicians from database");
        
        var politicians = politicianRepository.findAll().stream()
            .map(p -> new PoliticianDto(p.getId(), p.getName(), p.getParty()))
            .toList();
        
        log.debug("Found {} politicians in database", politicians.size());
        return new PoliticiansListResponse(politicians);
    }
    
    /**
     * Get politician trades with optional filters
     * 
     * @param limit Max number of trades (default 500, max 500)
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param ticker Filter by ticker symbol
     * @param politicianId Filter by politician UUID
     * @param filterLateReports Only show trades reported >45 days after transaction
     * @param page Page number (default 1)
     * @return List of politician trades enriched with party affiliation
     */
    public PoliticianTradesResponse getTrades(
            Integer limit, 
            String date, 
            String ticker, 
            UUID politicianId, 
            Boolean filterLateReports, 
            Integer page) {
        
        log.debug("Fetching politician trades with filters");
        var response = uwClient.getPoliticianTrades(limit, date, ticker, politicianId, filterLateReports, page);
        
        // Enrich trades with party data from database
        if (response != null && response.data() != null) {
            var enrichedTrades = response.data().stream()
                .map(this::enrichTradeWithParty)
                .toList();
            
            return new PoliticianTradesResponse(enrichedTrades);
        }
        
        return response;
    }
    
    /**
     * Enrich a single trade with party affiliation from database
     * Uses lazy enrichment: fetches from Congress.gov API if party is unknown/stale
     * Auto-creates missing politician entities to enable enrichment for all trades
     * 
     * @param trade Trade DTO from upstream market data API
     * @return Enriched trade with party data
     */
    private PoliticianTradeDto enrichTradeWithParty(PoliticianTradeDto trade) {
        String party = "UNK"; // Default to unknown
        
        if (trade.politicianId() != null && trade.name() != null) {
            var politicianOpt = politicianRepository.findById(trade.politicianId());
            
            Politician politician;
            if (politicianOpt.isEmpty()) {
                // Auto-create missing politician entity to enable lazy enrichment
                log.debug("Auto-creating politician entity for: {} (ID: {})", trade.name(), trade.politicianId());
                politician = Politician.builder()
                    .id(trade.politicianId())
                    .name(trade.name())
                    .isActive(true)
                    .memberType(MemberType.fromString(trade.memberType()))
                    .party("UNK") // Will be enriched below
                    .build();
                politician = politicianRepository.save(politician);
            } else {
                politician = politicianOpt.get();
            }
            
            // Lazy enrich if needed (party UNK or >30 days old)
            partyEnrichmentService.enrichPoliticianIfNeeded(politician);
            
            // Get party from database (may have just been enriched)
            party = politician.getParty();
        }
        
        // Create new DTO with party data
        return new PoliticianTradeDto(
            trade.amounts(),
            trade.filedAtDate(),
            trade.isActive(),
            trade.issuer(),
            trade.memberType(),
            trade.name(),
            trade.notes(),
            party,
            trade.politicianId(),
            trade.reporter(),
            trade.ticker(),
            trade.transactionDate(),
            trade.txnType()
        );
    }
    
    /**
     * Get a specific politician's portfolios and holdings
     * 
     * @param politicianId UUID of the politician
     * @param aggregateAllPortfolios Whether to combine all portfolios into one
     * @return Politician's portfolios with stock/option/crypto holdings
     */
    public PoliticianPortfoliosResponse getPoliticianPortfolio(UUID politicianId, Boolean aggregateAllPortfolios) {
        log.debug("Fetching portfolio for politician {}", politicianId);
        return uwClient.getPoliticianPortfolios(politicianId, aggregateAllPortfolios);
    }
    
    /**
     * Get congress trades data (used to fetch politician names for missing politicians)
     * 
     * @return Congress trades response with politician information
     */
    public CongressTradesResponse getCongressTrades() {
        log.debug("Fetching congress trades for politician data");
        return uwClient.getCongressTrades();
    }
    
    /**
     * Get recent congress trades with optional filters
     * 
     * @param date Filter by transaction date (YYYY-MM-DD)
     * @param limit Max trades to return
     * @param ticker Filter by ticker symbol
     * @return Congress trades response with filtered trades
     */
    public CongressTradesResponse getCongressRecentTrades(String date, Integer limit, String ticker) {
        log.debug("Fetching congress recent trades: date={}, limit={}, ticker={}", date, limit, ticker);
        return uwClient.getCongressRecentTrades(date, limit, ticker);
    }
}
