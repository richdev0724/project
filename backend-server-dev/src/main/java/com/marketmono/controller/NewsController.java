package com.marketmono.controller;

import com.marketmono.dto.NewsResponseDto;
import com.marketmono.dto.CompanyProfileDto;
import com.marketmono.dto.TopHeadlinesResponseDto;
import com.marketmono.service.NewsService;
import com.marketmono.service.TopHeadlinesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller for news headlines
 * Endpoints: GET /api/v1/news/headlines (merged provider feed)
 *            GET /api/v1/news/top-headlines (NewsAPI.org)
 */
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class NewsController {
    
    private final NewsService newsService;
    private final TopHeadlinesService topHeadlinesService;
    
    /**
     * Get financial news headlines with optional filters
     * 
     * @param sources Comma-separated news sources (e.g., "Reuters,Bloomberg")
     * @param searchTerm Search term to filter headlines
     * @param majorOnly Include only major news items
     * @param limit Number of results (default 50, max 100)
     * @param page Page number for pagination
     * @param ticker Filter by ticker symbol (e.g., "AAPL")
     * @param source Singular provider selector (newsapi, newsapiai, market_data); omit for merged feed
     * @param topic NewsAPI.ai/Event Registry topic when source=newsapiai
     * @param date Market-data headline date in YYYY-MM-DD format
     * @return News headlines response
     */
    @GetMapping("/headlines")
    public ResponseEntity<NewsResponseDto> getNewsHeadlines(
            @RequestParam(required = false) String sources,
            @RequestParam(name = "search_term", required = false) String searchTerm,
            @RequestParam(name = "major_only", required = false) Boolean majorOnly,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("GET /api/v1/news/headlines - sources: {}, searchTerm: {}, majorOnly: {}, limit: {}, page: {}, ticker: {}, source: {}, topic: {}, date: {}",
            sources, searchTerm, majorOnly, limit, page, ticker, source, topic, date);
        
        try {
            NewsResponseDto response = date == null
                ? newsService.getNewsHeadlines(
                    sources, searchTerm, majorOnly, limit, page, ticker, source, topic
                )
                : newsService.getNewsHeadlines(
                    sources, searchTerm, majorOnly, limit, page, ticker, source, topic, date
                );
            
            if (response != null && response.data() != null) {
                log.info("Returning {} news headlines", response.data().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No news headlines available");
                return ResponseEntity.ok(new NewsResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching news headlines", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/company-profile/{ticker}")
    public ResponseEntity<CompanyProfileDto> getCompanyProfile(@PathVariable String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        CompanyProfileDto profile = newsService.getCompanyProfile(ticker);
        return profile == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(profile);
    }
    
    /**
     * Get top headlines from NewsAPI.org
     * Note: country/category cannot be mixed with the sources param.
     *
     * @param country  2-letter ISO 3166-1 country code (e.g. "us")
     * @param category Category filter (business, entertainment, general, health, science, sports, technology)
     * @param sources  Comma-separated source identifiers
     * @param q        Keywords or phrase to search for
     * @param pageSize Number of results per page (default 20, max 100)
     * @param page     Page number for pagination
     * @return Top headlines response
     */
    @GetMapping("/top-headlines")
    public ResponseEntity<TopHeadlinesResponseDto> getTopHeadlines(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sources,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer page
    ) {
        log.info("GET /api/v1/news/top-headlines - country: {}, category: {}, sources: {}, q: {}, pageSize: {}, page: {}",
                country, category, sources, q, pageSize, page);
        
        try {
            TopHeadlinesResponseDto response = topHeadlinesService.getTopHeadlines(
                    country, category, sources, q, pageSize, page
            );
            
            if (response != null && response.articles() != null) {
                log.info("Returning {} top headlines", response.articles().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No top headlines available");
                return ResponseEntity.ok(new TopHeadlinesResponseDto("ok", 0, java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching top headlines", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
