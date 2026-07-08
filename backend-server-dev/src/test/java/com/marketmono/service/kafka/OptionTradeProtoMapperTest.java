package com.marketmono.service.kafka;

import com.marketmono.entity.OptionTrade;
import com.marketmono.proto.client.TransactionsProto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OptionTradeProtoMapperTest {

    private final OptionTradeProtoMapper mapper = new OptionTradeProtoMapper();

    @Test
    void shouldMarkTradeAsMultilegWhenTradeCodeStartsWithMultiLegPrefix() {
        TransactionsProto.OptionTrade proto = baseTrade()
            .setUpstreamCondDetails("mcpl")
            .build();

        OptionTrade entity = mapper.toEntity(proto);

        assertThat(entity).isNotNull();
        assertThat(entity.getIsMultileg()).isTrue();
        assertThat(entity.getCondition()).isEqualTo("MCPL");
    }

    @Test
    void shouldIncludeTradeCodeInConditionSignatureWithoutLosingReportFlags() {
        TransactionsProto.OptionTrade proto = baseTrade()
            .setUpstreamCondDetails("oseq")
            .addReportFlags("cross")
            .build();

        OptionTrade entity = mapper.toEntity(proto);

        assertThat(entity).isNotNull();
        assertThat(entity.getIsMultileg()).isFalse();
        assertThat(entity.getCondition()).isEqualTo("OSEQ|cross");
        assertThat(entity.getIsSweep()).isFalse();
    }

    @Test
    void shouldFallbackToTagsWhenTradeCodeIsMissing() {
        TransactionsProto.OptionTrade proto = baseTrade()
            .addTags("multi_leg")
            .build();

        OptionTrade entity = mapper.toEntity(proto);

        assertThat(entity).isNotNull();
        assertThat(entity.getIsMultileg()).isTrue();
    }

    private TransactionsProto.OptionTrade.Builder baseTrade() {
        return TransactionsProto.OptionTrade.newBuilder()
            .setId(UUID.randomUUID().toString())
            .setUnderlyingSymbol("SPY")
            .setExecutedAt(Instant.parse("2026-04-18T14:00:00Z").toEpochMilli())
            .setOptionChain("SPY260418C00500000")
            .setPrice("1.23")
            .setSize(10)
            .setUnderlyingPrice("500.00")
            .setPremium("12300")
            .setExchange("CBOE")
            .setExpiry("2026-04-18")
            .setOptionType("call")
            .addTags("ask_side");
    }
}