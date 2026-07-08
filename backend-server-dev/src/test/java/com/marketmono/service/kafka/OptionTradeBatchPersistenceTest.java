package com.marketmono.service.kafka;

import com.marketmono.config.KafkaConfig;
import com.marketmono.entity.OptionTrade;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeBatchPersistenceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private KafkaConfig kafkaConfig;
    private OptionTradeBatchPersistence persistence;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        kafkaConfig.getConsumer().setBatchSize(1);
        persistence = new OptionTradeBatchPersistence(jdbcTemplate, new SimpleMeterRegistry(), kafkaConfig);
        persistence.initMetrics();
    }

    @Test
    void shouldApplyConfiguredJdbcBatchSize() {
        List<OptionTrade> trades = List.of(sampleTrade(), sampleTrade());
        when(jdbcTemplate.batchUpdate(anyString(), eq(trades), eq(1), any()))
            .thenReturn(new int[][]{{1}, {1}});

        persistence.persistBatch(trades);

        verify(jdbcTemplate).batchUpdate(anyString(), eq(trades), eq(1), any());
    }

    private OptionTrade sampleTrade() {
        return OptionTrade.builder()
            .tradeId(UUID.randomUUID())
            .ticker("SPY")
            .price(new BigDecimal("1.23"))
            .size(10)
            .underlyingPrice(new BigDecimal("500.00"))
            .exchange("CBOE")
            .executedAt(Instant.parse("2026-03-18T14:30:00Z"))
            .optionChain("SPY_260320_C_510")
            .condition("AUTO")
            .isMultileg(false)
            .isFloorTrade(false)
            .isSweep(false)
            .aggressor("BUY")
            .premium(new BigDecimal("1230.00"))
            .side("BUY")
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .openInterest(1000)
            .volume(1200L)
            .build();
    }
}
