package com.marketmono.dto;

/**
 * DTO for lit pool top activity response
 * Matches TypeScript interface from frontend: {symbol?: string, lit_volume?: number}
 *
 * @param symbol Stock ticker symbol (optional in frontend)
 * @param litVolume Total lit pool volume for this symbol (optional in frontend)
 */
public record LitPoolTopActivityDto(
    String symbol,
    long litVolume
) {
}
