package com.marketmono.client;

import com.marketmono.dto.CongressGovMemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP client for Congress.gov API
 * Fetches congressional member data including party affiliations
 * 
 * API Documentation: https://github.com/LibraryOfCongress/api.congress.gov
 * Free API key required from: https://api.data.gov/signup
 * Rate limit: 1,000 requests/hour
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CongressGovClient {
    
    private final WebClient webClient;
    
    @Value("${congress-gov.api-key:}")
    private String apiKey;
    
    @Value("${congress-gov.base-url:https://api.congress.gov/v3}")
    private String baseUrl;
    
    /**
     * Fetch current members of Congress with party affiliations
     * Cached for 24 hours since Congress membership changes infrequently
     * Handles pagination automatically to fetch all 539+ current members
     * 
     * @param limit Not used - fetches all members via pagination
     * @return Response containing ALL members (across multiple pages)
     */
    @Cacheable(value = "apiResponses", key = "'congress-members-all'", unless = "#result == null")
    public CongressGovMemberResponse getCurrentMembers(Integer limit) {
        log.debug("Fetching ALL current Congress members with pagination");
        
        var allMembers = new java.util.ArrayList<com.marketmono.dto.CongressMember>();
        final int pageSize = 250; // API max limit per request
        int currentOffset = 0;
        int pagesProcessed = 0;
        boolean hasMore = true;
        
        while (hasMore) {
            final int offset = currentOffset; // Make effectively final for lambda
            log.debug("Fetching page {}: offset={}", pagesProcessed + 1, offset);
            
            var response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("api.congress.gov")
                    .path("/v3/member")
                    .queryParam("api_key", apiKey)
                    .queryParam("currentMember", "true")
                    .queryParam("offset", offset)
                    .queryParam("limit", pageSize)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .bodyToMono(CongressGovMemberResponse.class)
                .doOnError(error -> log.error("Failed to fetch Congress members at offset {}", offset, error))
                .block();
            
            if (response != null && response.members() != null && !response.members().isEmpty()) {
                allMembers.addAll(response.members());
                pagesProcessed++;
                log.debug("Page {}: Fetched {} members, total: {}", 
                         pagesProcessed, response.members().size(), allMembers.size());
                
                // Continue if we got a full page (more data likely exists)
                hasMore = response.members().size() == pageSize;
                currentOffset += pageSize;
            } else {
                hasMore = false;
            }
        }
        
        log.info("Fetched {} total current Congress members across {} pages", 
                 allMembers.size(), pagesProcessed);
        
        return new CongressGovMemberResponse(allMembers);
    }
}
