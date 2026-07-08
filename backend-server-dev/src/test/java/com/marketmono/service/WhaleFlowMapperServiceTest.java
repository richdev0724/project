package com.marketmono.service;

import com.marketmono.dto.WhaleTradeDto;
import com.marketmono.entity.FlowAlert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WhaleFlowMapperServiceTest {

    private final WhaleFlowMapperService mapper = new WhaleFlowMapperService();

    @Test
    void shouldUseNeutralExchangeFallbackWhenOptionSymbolHasNoExchange() {
        FlowAlert alert = new FlowAlert();
        alert.setAlertId(UUID.randomUUID());
        alert.setTicker("SPY");
        alert.setPrice(new BigDecimal("525.123"));
        alert.setTotalSize(10);
        alert.setTotalPremium(new BigDecimal("1250.125"));
        alert.setTradeStartTime(Instant.parse("2026-06-16T14:00:00Z"));
        alert.setOptionSymbol(null);

        WhaleTradeDto dto = mapper.toDto(alert);

        assertThat(dto.exchange_id()).isEqualTo("MARKET_DATA");
    }
}
