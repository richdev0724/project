package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Congressional member data from Congress.gov API
 * Includes party affiliation, state, chamber, and bioguide ID
 */
public record CongressMember(
    @JsonProperty("bioguideId")
    String bioguideId,
    
    String name,
    
    @JsonProperty("partyName")
    String partyName,  // "Democratic", "Republican", "Independent"
    
    String state,
    
    String chamber,  // "House of Representatives", "Senate"
    
    String district  // For House members only
) {
    /**
     * Convert full party name to code
     * 
     * @return D, R, I, or UNK for unknown
     */
    public String getPartyCode() {
        if (partyName == null) return "UNK";
        
        return switch (partyName.toLowerCase()) {
            case "democratic", "democrat" -> "D";
            case "republican" -> "R";
            case "independent" -> "I";
            default -> "UNK";
        };
    }
}
