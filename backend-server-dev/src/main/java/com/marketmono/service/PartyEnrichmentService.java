package com.marketmono.service;

import com.marketmono.client.CongressGovClient;
import com.marketmono.entity.Politician;
import com.marketmono.repository.PoliticianRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for enriching politician data with party affiliations from Congress.gov API
 * Uses lazy enrichment: only fetches when party is unknown or stale
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartyEnrichmentService {
    
    private final CongressGovClient congressGovClient;
    private final PoliticianRepository politicianRepository;
    
    // Fuzzy matching configuration
    private static final double SIMILARITY_THRESHOLD = 0.85; // 85% weighted similarity required for match
    private static final double JARO_WINKLER_WEIGHT = 0.6;   // 60% weight for Jaro-Winkler (better for names)
    private static final double LEVENSHTEIN_WEIGHT = 0.4;    // 40% weight for Levenshtein (handles middle initials)
    private static final int MAX_NAME_NORMALIZATION_LENGTH = 200;
    private static final Pattern TITLE_PATTERN = Pattern.compile(
        "\\b(hon\\.|hon|senator|sen\\.|sen|representative|rep\\.|rep|dr\\.|dr|mr\\.|mr|ms\\.|ms|mrs\\.|mrs)\\b"
    );
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern QUOTED_NICKNAME_PATTERN = Pattern.compile("\"[^\"]+\"");
    
    // Nickname mappings for fuzzy matching (lowercase keys)
    private static final Map<String, String> NICKNAME_MAP = Map.ofEntries(
        // Standard nickname → full name mappings
        Map.entry("tom", "thomas"),
        Map.entry("don", "donald"),
        Map.entry("bob", "robert"),
        Map.entry("jim", "james"),
        Map.entry("dan", "daniel"),
        Map.entry("mike", "michael"),
        Map.entry("dick", "richard"),
        Map.entry("bill", "william"),
        Map.entry("joe", "joseph"),
        Map.entry("ted", "theodore"),
        Map.entry("rich", "richard"),
        Map.entry("deb", "deborah"),
        Map.entry("debbie", "deborah"),
        Map.entry("steve", "steven"),
        Map.entry("ben", "benjamin"),
        Map.entry("chuck", "charles"),
        Map.entry("mitch", "mitchell"),
        Map.entry("dave", "david"),
        // Reverse mappings: full name → preferred name (for Congress.gov API)
        Map.entry("clifford", "scott"),   // Clifford Scott Franklin uses "Scott"
        Map.entry("valerie", "val"),      // Valerie Anne Hoyle uses "Val"
        Map.entry("rick", "richard")      // Rick Allen ↔ Richard (bidirectional)
    );
    
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
    
    /**
     * Check if politician needs party enrichment
     * Returns true if party is UNK or data is older than 30 days
     */
    public boolean needsPartyEnrichment(Politician politician) {
        if (politician.getParty() == null || "UNK".equals(politician.getParty())) {
            return true;
        }
        
        if (politician.getPartyUpdatedAt() != null) {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            return politician.getPartyUpdatedAt().isBefore(thirtyDaysAgo);
        }
        
        return true; // No timestamp, needs refresh
    }
    
    /**
     * Lazy enrich single politician with party data
     * Only makes API call if enrichment is needed
     * 
     * @param politician Politician entity to enrich
     * @return true if party was enriched successfully
     */
    @Transactional
    public boolean enrichPoliticianIfNeeded(Politician politician) {
        if (!needsPartyEnrichment(politician)) {
            log.trace("Politician {} party is current: {}", politician.getName(), politician.getParty());
            return true;
        }
        
        log.debug("Enriching politician {} with party data", politician.getName());
        
        try {
            // Fetch ALL current Congress members (539 total as of 2025)
            var congressResponse = congressGovClient.getCurrentMembers(600);
            if (congressResponse == null || congressResponse.members() == null) {
                log.warn("No Congress data for party enrichment");
                return false;
            }
            
            // Find matching Congress member by normalized name
            String normalizedName = normalizeName(politician.getName());
            String partyCode = congressResponse.members().stream()
                .filter(m -> normalizeName(m.name()).equals(normalizedName))
                .findFirst()
                .map(m -> m.getPartyCode())
                .orElse("UNK");
            
            // Update politician
            politician.setParty(partyCode);
            politician.setPartyUpdatedAt(LocalDateTime.now());
            politicianRepository.save(politician);
            
            log.debug("Enriched {} with party: {}", politician.getName(), partyCode);
            return true;
            
        } catch (Exception e) {
            log.error("Error enriching politician {} with party", politician.getName(), e);
            return false;
        }
    }
    
    /**
     * Enrich all politicians with party affiliation data from Congress.gov API
     * Matches by politician name (case-insensitive, fuzzy match with nickname expansion)
     * 
     * @return Number of politicians successfully enriched with party data
     */
    @Transactional
    public int enrichAllPoliticiansWithParty() {
        log.info("Starting party enrichment for all politicians");
        
        // Fetch ALL current Congress members (539 total as of 2025)
        var congressResponse = congressGovClient.getCurrentMembers(600);
        
        if (congressResponse == null || congressResponse.members() == null) {
            log.warn("No Congress member data received from API");
            return 0;
        }
        
        // Build TWO mappings: normalized name -> party AND normalized name -> original name
        Map<String, String> nameToParty = new HashMap<>();
        Map<String, String> normalizedToOriginal = new HashMap<>();
        for (var member : congressResponse.members()) {
            if (member.name() != null && member.partyName() != null) {
                String normalizedName = normalizeName(member.name());
                nameToParty.put(normalizedName, member.getPartyCode());
                normalizedToOriginal.put(normalizedName, member.name().toLowerCase()); // Store original for fuzzy matching
            }
        }
        
        log.info("Built party mapping for {} Congress members", nameToParty.size());
        
        // Fetch all politicians from database
        List<Politician> allPoliticians = politicianRepository.findAll();
        log.info("Enriching {} politicians from database", allPoliticians.size());
        
        int enrichedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        
        for (Politician politician : allPoliticians) {
            String normalizedName = normalizeName(politician.getName());
            String partyCode = nameToParty.get(normalizedName);
            
            // Fallback 1: Try partial last name match if exact match fails
            if (partyCode == null) {
                partyCode = fuzzyMatchByLastName(normalizedName, nameToParty, normalizedToOriginal);
            }
            
            // Fallback 2: Try weighted Levenshtein + Jaro-Winkler fuzzy matching
            if (partyCode == null) {
                partyCode = fuzzyMatchByWeightedSimilarity(normalizedName, nameToParty);
            }
            
            if (partyCode != null) {
                politician.setParty(partyCode);
                politician.setPartyUpdatedAt(now);
                enrichedCount++;
                log.debug("Enriched {} with party {}", politician.getName(), partyCode);
            } else {
                // Set to UNK if not found
                politician.setParty("UNK");
                politician.setPartyUpdatedAt(now);
                log.debug("No party found for {}, set to UNK", politician.getName());
            }
        }
        
        // Batch save all politicians
        politicianRepository.saveAll(allPoliticians);
        
        log.info("Party enrichment complete: {}/{} politicians matched", 
                 enrichedCount, allPoliticians.size());
        
        return enrichedCount;
    }
    
    /**
     * Fuzzy match by last name when exact match fails
     * Handles cases like:
     * - "Debbie Wasserman" vs "Deborah Wasserman Schultz"
     * - Multi-word last names that get simplified differently
     * 
     * @param politicianName Normalized politician name from database
     * @param nameToParty Map of Congress member names to party codes
     * @param normalizedToOriginal Map of normalized names to original Congress API names (for fuzzy matching)
     * @return Party code if fuzzy match found, null otherwise
     */
    private String fuzzyMatchByLastName(String politicianName, 
                                         Map<String, String> nameToParty,
                                         Map<String, String> normalizedToOriginal) {
        String[] politicianWords = politicianName.split("\\s+");
        if (politicianWords.length < 2) return null;
        
        String politicianLastName = politicianWords[politicianWords.length - 1];
        String politicianFirstName = politicianWords[0];
        
        log.debug("Fuzzy matching: politician='{}', firstName='{}', lastName='{}'", 
                 politicianName, politicianFirstName, politicianLastName);
        
        // Find Congress members with matching first name and partial last name
        for (Map.Entry<String, String> entry : nameToParty.entrySet()) {
            String normalizedCongressName = entry.getKey();
            String originalCongressName = normalizedToOriginal.get(normalizedCongressName);
            
            if (originalCongressName == null) continue;
            
            String[] congressWords = normalizedCongressName.split("\\s+");
            if (congressWords.length >= 2) {
                String congressFirstName = congressWords[0];
                
                // Check if:
                // 1. First names match exactly
                // 2. Politician's last name appears anywhere in ORIGINAL Congress name
                //    (handles "wasserman" being part of "wasserman schultz, deborah")
                if (congressFirstName.equals(politicianFirstName) && 
                    originalCongressName.contains(politicianLastName)) {
                    log.debug("Fuzzy matched '{}' to '{}' (original: '{}') by partial last name", 
                             politicianName, normalizedCongressName, originalCongressName);
                    return entry.getValue();
                }
            }
        }
        
        log.debug("No fuzzy match found for '{}'", politicianName);
        return null;
    }
    
    /**
     * Advanced fuzzy matching using weighted combination of Levenshtein + Jaro-Winkler distances
     * Handles edge cases like middle initials, typos, and name variations
     * 
     * Algorithm:
     * - Jaro-Winkler (60% weight): Better for prefix similarities and transpositions in names
     * - Levenshtein (40% weight): Better for handling insertions/deletions (middle initials)
     * - Threshold: 85% weighted similarity required for match
     * 
     * Examples:
     * - "charles fleishmann" vs "chuck fleischmann" → Likely match (nickname + typo)
     * - "richard allen" vs "rick allen" → Match (nickname expansion)
     * - "dave mccormick" vs "david mccormick" → Match (nickname expansion)
     * 
     * @param politicianName Normalized politician name from database
     * @param nameToParty Map of Congress member names to party codes
     * @return Party code if weighted similarity exceeds threshold, null otherwise
     */
    private String fuzzyMatchByWeightedSimilarity(String politicianName,
                                                   Map<String, String> nameToParty) {
        double bestScore = 0.0;
        String bestMatchParty = null;
        String bestMatchName = null;
        
        for (Map.Entry<String, String> entry : nameToParty.entrySet()) {
            String congressName = entry.getKey();
            double score = calculateWeightedSimilarity(politicianName, congressName);
            
            if (score > bestScore) {
                bestScore = score;
                bestMatchParty = entry.getValue();
                bestMatchName = congressName;
            }
        }
        
        if (bestScore >= SIMILARITY_THRESHOLD) {
            log.info("Weighted fuzzy match: '{}' → '{}' (score: {:.2f}%, party: {})", 
                     politicianName, bestMatchName, bestScore * 100, bestMatchParty);
            return bestMatchParty;
        }
        
        log.debug("No weighted fuzzy match for '{}' (best score: {:.2f}% < {:.2f}% threshold)", 
                 politicianName, bestScore * 100, SIMILARITY_THRESHOLD * 100);
        return null;
    }
    
    /**
     * Calculate weighted similarity score combining Jaro-Winkler and Levenshtein distances
     * 
     * @param s1 First string (politician name)
     * @param s2 Second string (Congress member name)
     * @return Weighted similarity score (0.0 to 1.0)
     */
    private double calculateWeightedSimilarity(String s1, String s2) {
        // Jaro-Winkler similarity (0.0 to 1.0, where 1.0 is identical)
        double jaroWinklerScore = jaroWinkler.apply(s1, s2);
        
        // Levenshtein distance (number of edits needed)
        int levenshteinDist = levenshteinDistance.apply(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        // Normalize Levenshtein to similarity score (0.0 to 1.0)
        double levenshteinScore = (maxLength == 0) ? 1.0 : 1.0 - ((double) levenshteinDist / maxLength);
        
        // Weighted combination
        return (JARO_WINKLER_WEIGHT * jaroWinklerScore) + (LEVENSHTEIN_WEIGHT * levenshteinScore);
    }
    
    /**
     * Normalize politician name for matching
     * Handles both "FIRST LAST" and "LAST, FIRST" formats
     * Removes titles, middle initials, nicknames, and standardizes format
     * Includes common nickname/abbreviation mappings for improved matching
     * 
     * Examples:
     *   "Hon. Nancy Pelosi" → "nancy pelosi"
     *   "Pelosi, Nancy" → "nancy pelosi"
     *   "Crawford, Eric A. \"Rick\"" → "eric crawford"
     *   "Marjorie Taylor Greene" → "marjorie greene" (keeps hyphenated last names)
     *   "Tom Suozzi" → "thomas suozzi" (nickname expansion)
     *   "Don Beyer" → "donald beyer" (nickname expansion)
     *   "Debbie Wasserman" → "debbie wasserman" (no change, fuzzy match needed)
     *   "Adam B. Schiff" → "adam schiff" (removes middle initial with period)
     *   "Charles J Fleishmann" → "charles fleishmann" (removes middle initial without period)
     * 
     * @param name Raw politician name
     * @return Normalized "first last" format for matching
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        String boundedName = name.length() > MAX_NAME_NORMALIZATION_LENGTH
            ? name.substring(0, MAX_NAME_NORMALIZATION_LENGTH)
            : name;

        // Remove titles, prefixes, and suffixes
        String normalized = collapseWhitespace(TITLE_PATTERN.matcher(boundedName.toLowerCase()).replaceAll(""));

        // Remove nicknames in quotes (e.g., "Rick")
        normalized = QUOTED_NICKNAME_PATTERN.matcher(normalized).replaceAll("").trim();
        
        // Handle "LAST, FIRST" format (Congress API format)
        if (normalized.contains(",")) {
            String[] parts = normalized.split(",", 2);
            if (parts.length == 2) {
                String lastName = parts[0].trim();
                String firstName = parts[1].trim();
                
                // Remove middle initials (single letter followed by period OR just single letter)
                firstName = removeMiddleInitialTokens(firstName);
                
                // Return as "first last"
                normalized = firstName + " " + lastName;
            }
        }
        
        // Remove middle initials from "FIRST MIDDLE LAST" format (both with and without periods)
        // Examples: "Adam B. Schiff" → "Adam Schiff", "Charles J Fleishmann" → "Charles Fleishmann"
        normalized = removeMiddleInitialTokens(normalized);
        
        // Apply common nickname expansions BEFORE simplification
        normalized = expandNicknames(normalized);
        
        // Simplify multi-word names to first + last (handles "First Middle Last")
        String[] words = WHITESPACE_PATTERN.split(normalized);
        if (words.length > 2) {
            // Keep first word and last word (handles hyphenated last names)
            normalized = words[0] + " " + words[words.length - 1];
        }
        
        return collapseWhitespace(normalized);
    }

    private String removeMiddleInitialTokens(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder();
        for (String word : WHITESPACE_PATTERN.split(value.trim())) {
            if (!isMiddleInitial(word)) {
                if (!normalized.isEmpty()) {
                    normalized.append(' ');
                }
                normalized.append(word);
            }
        }
        return normalized.toString();
    }

    private boolean isMiddleInitial(String word) {
        if (word.length() == 1) {
            return isLowercaseAsciiLetter(word.charAt(0));
        }
        return word.length() == 2
            && word.charAt(1) == '.'
            && isLowercaseAsciiLetter(word.charAt(0));
    }

    private boolean isLowercaseAsciiLetter(char value) {
        return value >= 'a' && value <= 'z';
    }

    private String collapseWhitespace(String value) {
        return WHITESPACE_PATTERN.matcher(value).replaceAll(" ").trim();
    }
    
    /**
     * Expand common political nicknames to full first names using efficient Map-based lookups
     * Improves match rate for politicians who use nicknames
     * 
     * Performance: O(n*m) where n = number of words, m = average word length
     * Much faster than 21 sequential regex replaceAll() calls
     * 
     * @param name Normalized name
     * @return Name with nicknames expanded to full first names
     */
    private String expandNicknames(String name) {
        String[] words = WHITESPACE_PATTERN.split(name);
        for (int i = 0; i < words.length; i++) {
            String replacement = NICKNAME_MAP.get(words[i]);
            if (replacement != null) {
                words[i] = replacement;
            }
        }
        return String.join(" ", words);
    }
}
