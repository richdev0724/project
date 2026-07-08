package com.marketmono.service;

import com.marketmono.entity.OptionTrade;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.entity.OptionTradeAggregateMember;
import com.marketmono.config.OptionTradeAggregationProperties;
import com.marketmono.repository.OptionTradeAggregateMemberRepository;
import com.marketmono.repository.OptionTradeAggregateRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeAggregationCoordinatorTest {

    @Mock
    private OptionTradeAggregateRepository aggregateRepository;

    @Mock
    private OptionTradeAggregateMemberRepository memberRepository;

    @Mock
    private OptionTradeAggregateBroadcaster aggregateBroadcaster;

    private OptionTradeAggregationCoordinator coordinator;
    private OptionTradeAggregationProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        properties = new OptionTradeAggregationProperties();
        properties.setAsync(false);
        meterRegistry = new SimpleMeterRegistry();
        coordinator = coordinator(properties);
    }

    @AfterEach
    void tearDown() {
        coordinator.destroy();
    }

    @Test
    void shouldGroupSimilarTradesIntoSingleAggregate() {
        OptionTrade first = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:00:00Z"),
            new BigDecimal("1.00"),
            10,
            new BigDecimal("1000.00"),
            new BigDecimal("500.00"),
            1000L,
            "CBOE",
            false
        );
        OptionTrade second = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:00:01Z"),
            new BigDecimal("1.01"),
            20,
            new BigDecimal("2000.00"),
            new BigDecimal("500.03"),
            1020L,
            "NYSE",
            false
        );

        when(memberRepository.findByTradeIdIn(anyList())).thenReturn(List.of());

        boolean accepted = coordinator.aggregateBatch(List.of(first, second));

        ArgumentCaptor<Iterable<OptionTradeAggregate>> aggregateCaptor = iterableCaptor();
        ArgumentCaptor<Iterable<OptionTradeAggregateMember>> memberCaptor = iterableCaptor();
        ArgumentCaptor<List<OptionTradeAggregate>> broadcastCaptor = listCaptor();

        assertThat(accepted).isTrue();
        verify(aggregateRepository).saveAll(aggregateCaptor.capture());
        verify(memberRepository).saveAll(memberCaptor.capture());
        verify(aggregateBroadcaster).broadcastAggregates(broadcastCaptor.capture());

        List<OptionTradeAggregate> aggregates = toList(aggregateCaptor.getValue());
        List<OptionTradeAggregateMember> members = toList(memberCaptor.getValue());
        List<OptionTradeAggregate> broadcasted = broadcastCaptor.getValue();

        assertThat(aggregates).hasSize(1);
        OptionTradeAggregate aggregate = aggregates.getFirst();
        assertThat(aggregate.getTicker()).isEqualTo("SPY");
        assertThat(aggregate.getOptionChain()).isEqualTo("SPY_260320_C_510");
        assertThat(aggregate.getTradeCount()).isEqualTo(2);
        assertThat(aggregate.getTotalSize()).isEqualTo(30L);
        assertThat(aggregate.getTotalPremium()).isEqualByComparingTo("3000.00");
        assertThat(aggregate.getRepresentativePrice()).isEqualByComparingTo("1.0067");
        assertThat(aggregate.getMinPrice()).isEqualByComparingTo("1.00");
        assertThat(aggregate.getMaxPrice()).isEqualByComparingTo("1.01");
        assertThat(aggregate.getExchangeCount()).isEqualTo(2);
        assertThat(aggregate.getVolume()).isEqualTo(1020L);
        assertThat(aggregate.getFirstExecutedAt()).isEqualTo(first.getExecutedAt());
        assertThat(aggregate.getLastExecutedAt()).isEqualTo(second.getExecutedAt());
        assertThat(aggregate.getAggregateId()).isNotNull();

        assertThat(members).hasSize(2);
        assertThat(members).extracting(OptionTradeAggregateMember::getAggregateId)
            .containsOnly(aggregate.getAggregateId());
        assertThat(members).extracting(OptionTradeAggregateMember::getTradeId)
            .containsExactly(first.getTradeId(), second.getTradeId());
        assertThat(members).extracting(OptionTradeAggregateMember::getMemberOrder)
            .containsExactly(0, 1);

        assertThat(broadcasted).hasSize(1);
        assertThat(broadcasted.getFirst().getAggregateId()).isEqualTo(aggregate.getAggregateId());
        assertThat(meterRegistry.get("option.trade.aggregation.trades").counter().count()).isEqualTo(2.0);
    }

    @Test
    void shouldKeepFloorTradesSeparate() {
        OptionTrade firstFloorTrade = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:05:00Z"),
            new BigDecimal("1.20"),
            15,
            new BigDecimal("1800.00"),
            new BigDecimal("501.00"),
            1200L,
            "CBOE",
            true
        );
        OptionTrade secondFloorTrade = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:05:01Z"),
            new BigDecimal("1.21"),
            15,
            new BigDecimal("1815.00"),
            new BigDecimal("501.02"),
            1215L,
            "NYSE",
            true
        );

        when(memberRepository.findByTradeIdIn(anyList())).thenReturn(List.of());

        boolean accepted = coordinator.aggregateBatch(List.of(firstFloorTrade, secondFloorTrade));

        ArgumentCaptor<Iterable<OptionTradeAggregate>> aggregateCaptor = iterableCaptor();
        assertThat(accepted).isTrue();
        verify(aggregateRepository).saveAll(aggregateCaptor.capture());

        List<OptionTradeAggregate> aggregates = toList(aggregateCaptor.getValue());
        assertThat(aggregates).hasSize(2);
        assertThat(aggregates).allMatch(aggregate -> Boolean.TRUE.equals(aggregate.getIsFloorTrade()));
        assertThat(aggregates).extracting(OptionTradeAggregate::getTradeCount).containsOnly(1);
        assertThat(aggregates).extracting(OptionTradeAggregate::getAggregateId).doesNotHaveDuplicates();
    }

    @Test
    void shouldChunkFreshnessLookupAndPersistence() {
        properties.setLookupChunkSize(1);
        properties.setSaveChunkSize(1);
        coordinator.destroy();
        coordinator = coordinator(properties);

        OptionTrade first = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:10:00Z"),
            new BigDecimal("1.20"),
            10,
            new BigDecimal("1200.00"),
            new BigDecimal("501.00"),
            1200L,
            "CBOE",
            true
        );
        OptionTrade second = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:10:01Z"),
            new BigDecimal("1.21"),
            10,
            new BigDecimal("1210.00"),
            new BigDecimal("501.02"),
            1210L,
            "NYSE",
            true
        );

        when(memberRepository.findByTradeIdIn(anyList())).thenReturn(List.of());

        boolean accepted = coordinator.aggregateBatch(List.of(first, second));

        assertThat(accepted).isTrue();
        verify(memberRepository, times(2)).findByTradeIdIn(argThat(ids -> ids.size() == 1));
        verify(aggregateRepository, times(2)).saveAll(anyList());
        verify(memberRepository, times(2)).saveAll(anyList());
        verify(aggregateBroadcaster, times(2)).broadcastAggregates(argThat(batch -> batch.size() == 1));
    }

    @Test
    void shouldChunkMembersEvenWhenOneGroupContainsManyTrades() {
        properties.setSaveChunkSize(1);
        coordinator.destroy();
        coordinator = coordinator(properties);

        OptionTrade first = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:15:00Z"),
            new BigDecimal("1.20"),
            10,
            new BigDecimal("1200.00"),
            new BigDecimal("501.00"),
            1200L,
            "CBOE",
            false
        );
        OptionTrade second = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:15:01Z"),
            new BigDecimal("1.21"),
            10,
            new BigDecimal("1210.00"),
            new BigDecimal("501.02"),
            1210L,
            "NYSE",
            false
        );

        when(memberRepository.findByTradeIdIn(anyList())).thenReturn(List.of());

        boolean accepted = coordinator.aggregateBatch(List.of(first, second));

        assertThat(accepted).isTrue();
        verify(aggregateRepository).saveAll(argThat(aggregates -> toList(aggregates).size() == 1));
        verify(memberRepository, times(2)).saveAll(argThat(members -> toList(members).size() == 1));
    }

    @Test
    void shouldRejectAndMetricBatchesWhenAsyncExecutorIsSaturated() throws Exception {
        properties.setAsync(true);
        properties.setMaxInFlightBatches(1);
        properties.setQueueCapacity(0);
        coordinator.destroy();
        coordinator = coordinator(properties);

        CountDownLatch firstBatchEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstBatch = new CountDownLatch(1);
        when(memberRepository.findByTradeIdIn(anyList())).thenAnswer(invocation -> {
            firstBatchEntered.countDown();
            releaseFirstBatch.await(2, TimeUnit.SECONDS);
            return List.of();
        });

        OptionTrade first = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:20:00Z"),
            new BigDecimal("1.20"),
            10,
            new BigDecimal("1200.00"),
            new BigDecimal("501.00"),
            1200L,
            "CBOE",
            false
        );
        OptionTrade second = trade(
            UUID.randomUUID(),
            Instant.parse("2026-03-18T14:21:00Z"),
            new BigDecimal("1.30"),
            10,
            new BigDecimal("1300.00"),
            new BigDecimal("502.00"),
            1300L,
            "CBOE",
            false
        );

        boolean firstAccepted = coordinator.aggregateBatch(List.of(first));
        assertThat(firstAccepted).isTrue();
        assertThat(firstBatchEntered.await(1, TimeUnit.SECONDS)).isTrue();

        boolean secondAccepted = coordinator.aggregateBatch(List.of(second));

        assertThat(secondAccepted).isFalse();
        assertThat(meterRegistry.get("option.trade.aggregation.skipped").counter().count()).isEqualTo(1.0);

        releaseFirstBatch.countDown();
        coordinator.awaitIdleForTests(2, TimeUnit.SECONDS);
    }

    private OptionTrade trade(
        UUID tradeId,
        Instant executedAt,
        BigDecimal price,
        int size,
        BigDecimal premium,
        BigDecimal underlyingPrice,
        long volume,
        String exchange,
        boolean floorTrade
    ) {
        return OptionTrade.builder()
            .tradeId(tradeId)
            .executedAt(executedAt)
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .price(price)
            .size(size)
            .premium(premium)
            .underlyingPrice(underlyingPrice)
            .exchange(exchange)
            .side("BUY")
            .aggressor("BUY")
            .condition("AUTO")
            .isSweep(false)
            .isFloorTrade(floorTrade)
            .isMultileg(false)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .openInterest(1000)
                .volume(volume)
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<Iterable<T>> iterableCaptor() {
        return ArgumentCaptor.forClass((Class) Iterable.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        return iterable == null ? List.of() : org.assertj.core.util.Lists.newArrayList(iterable);
    }

    private OptionTradeAggregationCoordinator coordinator(OptionTradeAggregationProperties aggregationProperties) {
        return new OptionTradeAggregationCoordinator(
            aggregateRepository,
            memberRepository,
            aggregateBroadcaster,
            aggregationProperties,
            meterRegistry,
            transactionOperations()
        );
    }

    private static TransactionOperations transactionOperations() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }
}
