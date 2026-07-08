package com.marketmono.service;

import com.marketmono.config.OptionTradesProperties;
import com.marketmono.repository.OptionTradeAggregateRepository;
import com.marketmono.repository.OptionTradeRepository;
import com.marketmono.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptionTradeMarketDayResolver {

    public static final String SOURCE_RAW = "raw";
    public static final String SOURCE_AGGREGATE_FALLBACK = "aggregate_fallback";

    private final OptionTradeRepository optionTradeRepository;
    private final OptionTradeAggregateRepository aggregateRepository;
    private final OptionTradesProperties optionTradesProperties;

    @Transactional(readOnly = true, timeout = 5)
    public Optional<ResolvedMarketDay> resolveRawDefaultDay() {
        Optional<LocalDate> rawMaxDay = latestRawMarketDay();
        Optional<LocalDate> aggregateMaxDay = latestAggregateMarketDay();

        return rawMaxDay.map(day -> resolved(day, SOURCE_RAW, rawMaxDay, aggregateMaxDay));
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<ResolvedMarketDay> resolveAggregateDefaultDay() {
        Optional<LocalDate> rawMaxDay = latestRawMarketDay();
        Optional<LocalDate> aggregateMaxDay = latestAggregateMarketDay();

        if (rawMaxDay.isPresent()) {
            LocalDate canonicalRawDay = rawMaxDay.get();
            if (hasAggregatesForDay(canonicalRawDay)) {
                return Optional.of(resolved(canonicalRawDay, SOURCE_RAW, rawMaxDay, aggregateMaxDay));
            }

            aggregateMaxDay.ifPresent(day -> log.warn(
                "[MARKET-DAY] Aggregate rows missing for canonical raw day {}; falling back to aggregate day {}",
                canonicalRawDay,
                day
            ));
            return aggregateMaxDay.map(day ->
                resolved(day, SOURCE_AGGREGATE_FALLBACK, rawMaxDay, aggregateMaxDay)
            );
        }

        return aggregateMaxDay.map(day ->
            resolved(day, SOURCE_AGGREGATE_FALLBACK, rawMaxDay, aggregateMaxDay)
        );
    }

    private Optional<LocalDate> latestRawMarketDay() {
        return Optional.ofNullable(optionTradeRepository.findMaxExecutedAtSince(lookbackSince()))
            .map(DateUtils::toEasternDate);
    }

    private Optional<LocalDate> latestAggregateMarketDay() {
        return Optional.ofNullable(aggregateRepository.findMaxLastExecutedAtSince(lookbackSince()))
            .map(DateUtils::toEasternDate);
    }

    private Instant lookbackSince() {
        int lookbackDays = Math.max(1, optionTradesProperties.getMarketDay().getFallbackLookbackDays());
        LocalDate startDay = DateUtils.getCurrentDateInEasternTime().minusDays(lookbackDays);
        return DateUtils.startOfEasternDay(startDay);
    }

    private boolean hasAggregatesForDay(LocalDate day) {
        Instant since = DateUtils.startOfEasternDay(day);
        Instant untilExclusive = DateUtils.startOfNextEasternDay(day);
        return aggregateRepository.existsByLastExecutedAtGreaterThanEqualAndLastExecutedAtLessThan(
            since,
            untilExclusive
        );
    }

    private ResolvedMarketDay resolved(
        LocalDate day,
        String source,
        Optional<LocalDate> rawMaxDay,
        Optional<LocalDate> aggregateMaxDay
    ) {
        return new ResolvedMarketDay(
            day,
            DateUtils.startOfEasternDay(day),
            DateUtils.endOfEasternDay(day),
            source,
            rawMaxDay.orElse(null),
            aggregateMaxDay.orElse(null)
        );
    }

    public record ResolvedMarketDay(
        LocalDate day,
        Instant since,
        Instant until,
        String source,
        LocalDate rawMaxDay,
        LocalDate aggregateMaxDay
    ) {}
}
