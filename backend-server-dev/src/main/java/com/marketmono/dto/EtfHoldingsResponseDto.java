package com.marketmono.dto;

import java.util.List;

public record EtfHoldingsResponseDto(
    List<EtfHoldingDto> data
) {}
