package com.marketmono.dto;

/**
 * DTO for dark pool top activity response
 * Matches TypeScript interface from frontend: {symbol: string, dark_volume: number}
 *
 * @param symbol Stock ticker symbol
 * @param darkVolume Total dark pool volume for this symbol
 */
public record DarkPoolTopActivityDto(
    String symbol,
    long darkVolume
) {
}
