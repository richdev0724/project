package com.marketmono.service;

import com.marketmono.config.OptionTradeAggregationProperties;
import com.marketmono.entity.OptionTrade;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.entity.OptionTradeAggregateMember;
import com.marketmono.repository.OptionTradeAggregateMemberRepository;
import com.marketmono.repository.OptionTradeAggregateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OptionTradeAggregationCoordinator implements DisposableBean {

    private static final Duration TIME_WINDOW = Duration.ofSeconds(2);
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.02");
    private static final BigDecimal UNDERLYING_TOLERANCE = new BigDecimal("0.05");
    private static final String METRIC_QUEUE_DEPTH = "option.trade.aggregation.queue.depth";
    private static final String METRIC_IN_FLIGHT = "option.trade.aggregation.in.flight";
    private static final String METRIC_SKIPPED = "option.trade.aggregation.skipped";
    private static final String METRIC_TRADES = "option.trade.aggregation.trades";
    private static final String METRIC_DURATION = "option.trade.aggregation.duration";
    private static final long SATURATION_LOG_INTERVAL_MS = 10_000;

    private final OptionTradeAggregateRepository aggregateRepository;
    private final OptionTradeAggregateMemberRepository memberRepository;
    private final OptionTradeAggregateBroadcaster aggregateBroadcaster;
    private final OptionTradeAggregationProperties properties;
    private final MeterRegistry meterRegistry;
    private final TransactionOperations transactionOperations;
    private final ThreadPoolExecutor aggregationExecutor;
    private final Counter skippedCounter;
    private final Counter tradesCounter;
    private final Timer durationTimer;
    private final AtomicLong saturationSkipsSinceLastLog = new AtomicLong();
    private final AtomicLong lastSaturationLogAtMs = new AtomicLong();

    public OptionTradeAggregationCoordinator(
        OptionTradeAggregateRepository aggregateRepository,
        OptionTradeAggregateMemberRepository memberRepository,
        OptionTradeAggregateBroadcaster aggregateBroadcaster,
        OptionTradeAggregationProperties properties,
        MeterRegistry meterRegistry,
        TransactionOperations transactionOperations
    ) {
        this.aggregateRepository = aggregateRepository;
        this.memberRepository = memberRepository;
        this.aggregateBroadcaster = aggregateBroadcaster;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.transactionOperations = transactionOperations;
        this.aggregationExecutor = createExecutor(properties);
        this.skippedCounter = Counter.builder(METRIC_SKIPPED)
            .description("Option trade aggregate batches skipped because aggregation was disabled or saturated")
            .register(meterRegistry);
        this.tradesCounter = Counter.builder(METRIC_TRADES)
            .description("Option trades accepted for aggregate derivation")
            .register(meterRegistry);
        this.durationTimer = Timer.builder(METRIC_DURATION)
            .description("Time spent deriving option trade aggregate batches")
            .register(meterRegistry);
        registerGauges();
    }

    public boolean aggregateBatch(List<OptionTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return true;
        }

        if (!properties.isEnabled()) {
            skippedCounter.increment();
            log.debug("[KAFKA] Aggregate derivation skipped because aggregation is disabled ({} trades)", trades.size());
            return false;
        }

        if (!properties.isAsync()) {
            runBatch(trades);
            return true;
        }

        try {
            aggregationExecutor.execute(() -> runBatch(trades));
            return true;
        } catch (RejectedExecutionException exception) {
            recordSaturationSkip(trades.size());
            return false;
        }
    }

    private void recordSaturationSkip(int tradeCount) {
        skippedCounter.increment();
        saturationSkipsSinceLastLog.incrementAndGet();

        long now = System.currentTimeMillis();
        long lastLoggedAt = lastSaturationLogAtMs.get();
        if (now - lastLoggedAt < SATURATION_LOG_INTERVAL_MS || !lastSaturationLogAtMs.compareAndSet(lastLoggedAt, now)) {
            return;
        }

        long recentSkips = saturationSkipsSinceLastLog.getAndSet(0);
        log.warn(
            "[KAFKA] Aggregate derivation skipped because bounded executor is saturated (recentSkips={}, latestTrades={}, active={}, queued={}, queueCapacity={})",
            recentSkips,
            tradeCount,
            aggregationExecutor.getActiveCount(),
            aggregationExecutor.getQueue().size(),
            properties.getQueueCapacity()
        );
    }

    private void runBatch(List<OptionTrade> trades) {
        try {
            durationTimer.record(() -> transactionOperations.executeWithoutResult(status -> aggregateInTransaction(trades)));
        } catch (Exception e) {
            log.warn("[KAFKA] Failed to derive option trade aggregates: {}", e.getMessage(), e);
        }
    }

    private void aggregateInTransaction(List<OptionTrade> trades) {
        tradesCounter.increment(trades.size());
        List<OptionTrade> freshTrades = filterFreshTrades(trades);
        if (freshTrades.isEmpty()) {
            log.debug("[KAFKA] Aggregate derivation skipped — all {} trades already linked", trades.size());
            return;
        }

        List<TradeGroup> groups = groupTrades(freshTrades);
        int aggregateCount = persistAndBroadcastGroups(groups);

        log.debug(
            "[KAFKA] Derived {} aggregate rows from {} fresh trades ({} received)",
            aggregateCount,
            freshTrades.size(),
            trades.size()
        );
    }

    private List<OptionTrade> filterFreshTrades(List<OptionTrade> trades) {
        List<UUID> tradeIds = trades.stream()
            .map(OptionTrade::getTradeId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Set<UUID> existingTradeIds = new HashSet<>();
        forEachChunk(tradeIds, properties.getLookupChunkSize(), chunk -> existingTradeIds.addAll(
            memberRepository.findByTradeIdIn(chunk).stream()
                .map(OptionTradeAggregateMember::getTradeId)
                .collect(Collectors.toSet())
        ));

        return trades.stream()
            .filter(trade -> !existingTradeIds.contains(trade.getTradeId()))
            .sorted(Comparator
                .comparing(OptionTrade::getExecutedAt)
                .thenComparing(OptionTrade::getTradeId))
            .toList();
    }

    private List<TradeGroup> groupTrades(List<OptionTrade> trades) {
        Map<GroupKey, List<TradeGroup>> groupsByKey = new LinkedHashMap<>();
        List<TradeGroup> groups = new ArrayList<>();

        for (OptionTrade trade : trades) {
            GroupKey key = GroupKey.from(trade);
            List<TradeGroup> candidates = groupsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
            TradeGroup matchingGroup = null;
            for (TradeGroup candidate : candidates) {
                if (candidate.canAccept(trade)) {
                    matchingGroup = candidate;
                    break;
                }
            }

            if (matchingGroup == null) {
                TradeGroup newGroup = new TradeGroup(trade);
                candidates.add(newGroup);
                groups.add(newGroup);
            } else {
                matchingGroup.add(trade);
            }
        }

        return groups;
    }

    private int persistAndBroadcastGroups(List<TradeGroup> groups) {
        int saveChunkSize = normalizedChunkSize(properties.getSaveChunkSize());
        List<OptionTradeAggregate> aggregateBuffer = new ArrayList<>(saveChunkSize);
        List<OptionTradeAggregateMember> memberBuffer = new ArrayList<>(saveChunkSize);
        int aggregateCount = 0;

        for (TradeGroup group : groups) {
            UUID aggregateId = UUID.randomUUID();
            aggregateBuffer.add(group.toAggregate(aggregateId));
            memberBuffer.addAll(group.toMembers(aggregateId));

            if (aggregateBuffer.size() >= saveChunkSize || memberBuffer.size() >= saveChunkSize) {
                aggregateCount += flushBuffers(aggregateBuffer, memberBuffer);
            }
        }

        aggregateCount += flushBuffers(aggregateBuffer, memberBuffer);
        return aggregateCount;
    }

    private int flushBuffers(
        List<OptionTradeAggregate> aggregateBuffer,
        List<OptionTradeAggregateMember> memberBuffer
    ) {
        if (aggregateBuffer.isEmpty()) {
            return 0;
        }

        List<OptionTradeAggregate> aggregates = List.copyOf(aggregateBuffer);
        List<OptionTradeAggregateMember> members = List.copyOf(memberBuffer);
        aggregateRepository.saveAll(aggregates);
        forEachChunk(members, properties.getSaveChunkSize(), memberRepository::saveAll);
        aggregateBroadcaster.broadcastAggregates(aggregates);
        aggregateBuffer.clear();
        memberBuffer.clear();
        return aggregates.size();
    }

    private <T> void forEachChunk(List<T> items, int configuredChunkSize, Consumer<List<T>> consumer) {
        if (items.isEmpty()) {
            return;
        }

        int chunkSize = normalizedChunkSize(configuredChunkSize);
        for (int start = 0; start < items.size(); start += chunkSize) {
            consumer.accept(items.subList(start, Math.min(start + chunkSize, items.size())));
        }
    }

    private int normalizedChunkSize(int configuredChunkSize) {
        return Math.max(1, configuredChunkSize);
    }

    private ThreadPoolExecutor createExecutor(OptionTradeAggregationProperties aggregationProperties) {
        int maxInFlight = Math.max(1, aggregationProperties.getMaxInFlightBatches());
        BlockingQueue<Runnable> queue = aggregationProperties.getQueueCapacity() > 0
            ? new ArrayBlockingQueue<>(aggregationProperties.getQueueCapacity())
            : new SynchronousQueue<>();

        return new ThreadPoolExecutor(
            maxInFlight,
            maxInFlight,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            aggregationThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private ThreadFactory aggregationThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "option-trade-aggregation-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private void registerGauges() {
        Gauge.builder(METRIC_QUEUE_DEPTH, aggregationExecutor, executor -> executor.getQueue().size())
            .description("Queued option trade aggregate batches")
            .register(meterRegistry);
        Gauge.builder(METRIC_IN_FLIGHT, aggregationExecutor, ThreadPoolExecutor::getActiveCount)
            .description("Option trade aggregate batches currently executing")
            .register(meterRegistry);
    }

    boolean awaitIdleForTests(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            if (aggregationExecutor.getActiveCount() == 0 && aggregationExecutor.getQueue().isEmpty()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    @Override
    public void destroy() {
        aggregationExecutor.shutdown();
        try {
            if (!aggregationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                aggregationExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            aggregationExecutor.shutdownNow();
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static boolean withinTolerance(BigDecimal left, BigDecimal right, BigDecimal tolerance) {
        if (left == null || right == null) {
            return true;
        }
        return left.subtract(right).abs().compareTo(tolerance) <= 0;
    }

    private static final class TradeGroup {
        private final GroupKey key;
        private final List<OptionTrade> members = new ArrayList<>();

        private TradeGroup(OptionTrade firstTrade) {
            this.key = GroupKey.from(firstTrade);
            this.members.add(firstTrade);
        }

        private void add(OptionTrade trade) {
            members.add(trade);
        }

        private boolean canAccept(OptionTrade trade) {
            if (!key.equals(GroupKey.from(trade))) {
                return false;
            }

            if (Boolean.TRUE.equals(key.isFloorTrade) || Boolean.TRUE.equals(trade.getIsFloorTrade())) {
                return false;
            }

            OptionTrade lastTrade = members.get(members.size() - 1);
            Duration gap = Duration.between(lastTrade.getExecutedAt(), trade.getExecutedAt()).abs();
            if (gap.compareTo(TIME_WINDOW) > 0) {
                return false;
            }

            if (!withinTolerance(lastTrade.getPrice(), trade.getPrice(), PRICE_TOLERANCE)) {
                return false;
            }

            return withinTolerance(lastTrade.getUnderlyingPrice(), trade.getUnderlyingPrice(), UNDERLYING_TOLERANCE);
        }

        private OptionTradeAggregate toAggregate(UUID aggregateId) {
            OptionTrade firstTrade = members.get(0);
            OptionTrade lastTrade = members.get(members.size() - 1);

            long totalSize = members.stream()
                .map(OptionTrade::getSize)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

            BigDecimal totalPremium = members.stream()
                .map(OptionTrade::getPremium)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal minPrice = members.stream()
                .map(OptionTrade::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);

            BigDecimal maxPrice = members.stream()
                .map(OptionTrade::getPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);

            BigDecimal weightedPriceSum = members.stream()
                .filter(trade -> trade.getPrice() != null && trade.getSize() != null)
                .map(trade -> trade.getPrice().multiply(BigDecimal.valueOf(trade.getSize())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal representativePrice = totalSize > 0
                ? weightedPriceSum.divide(BigDecimal.valueOf(totalSize), 4, RoundingMode.HALF_UP)
                : firstTrade.getPrice();

            Long volume = members.stream()
                .map(OptionTrade::getVolume)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(firstTrade.getVolume());

            int exchangeCount = (int) members.stream()
                .map(OptionTrade::getExchange)
                .map(OptionTradeAggregationCoordinator::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .count();

            return OptionTradeAggregate.builder()
                .aggregateId(aggregateId)
                .groupingVersion(1)
                .ticker(firstTrade.getTicker())
                .optionChain(firstTrade.getOptionChain())
                .side(key.side)
                .aggressor(key.aggressor)
                .conditionSignature(key.conditionSignature)
                .isSweep(Boolean.TRUE.equals(firstTrade.getIsSweep()))
                .isFloorTrade(Boolean.TRUE.equals(firstTrade.getIsFloorTrade()))
                .isMultileg(Boolean.TRUE.equals(firstTrade.getIsMultileg()))
                .firstExecutedAt(firstTrade.getExecutedAt())
                .lastExecutedAt(lastTrade.getExecutedAt())
                .representativePrice(representativePrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalSize(totalSize)
                .totalPremium(totalPremium)
                .tradeCount(members.size())
                .exchangeCount(exchangeCount)
                .expiry(firstTrade.getExpiry())
                .optionType(firstTrade.getOptionType())
                .strike(firstTrade.getStrike())
                .underlyingPriceFirst(firstTrade.getUnderlyingPrice())
                .underlyingPriceLast(lastTrade.getUnderlyingPrice())
                .openInterest(firstTrade.getOpenInterest())
                .volume(volume)
                .build();
        }

        private List<OptionTradeAggregateMember> toMembers(UUID aggregateId) {
            List<OptionTradeAggregateMember> aggregateMembers = new ArrayList<>(members.size());
            for (int i = 0; i < members.size(); i++) {
                OptionTrade trade = members.get(i);
                aggregateMembers.add(OptionTradeAggregateMember.builder()
                    .aggregateId(aggregateId)
                    .tradeId(trade.getTradeId())
                    .executedAt(trade.getExecutedAt())
                    .memberOrder(i)
                    .build());
            }
            return aggregateMembers;
        }
    }

    private record GroupKey(
        String ticker,
        String optionChain,
        String side,
        String aggressor,
        String conditionSignature,
        Boolean isSweep,
        Boolean isFloorTrade,
        Boolean isMultileg
    ) {
        private static GroupKey from(OptionTrade trade) {
            return new GroupKey(
                normalize(trade.getTicker()),
                normalize(trade.getOptionChain()),
                normalize(trade.getSide()),
                normalize(trade.getAggressor()),
                normalize(trade.getCondition()),
                Boolean.TRUE.equals(trade.getIsSweep()),
                Boolean.TRUE.equals(trade.getIsFloorTrade()),
                Boolean.TRUE.equals(trade.getIsMultileg())
            );
        }
    }
}
