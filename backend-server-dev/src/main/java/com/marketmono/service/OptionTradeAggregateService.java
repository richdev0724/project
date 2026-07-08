package com.marketmono.service;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.OptionTrade;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.entity.OptionTradeAggregateMember;
import com.marketmono.entity.OptionTradeId;
import com.marketmono.repository.OptionTradeAggregateMemberRepository;
import com.marketmono.repository.OptionTradeAggregateRepository;
import com.marketmono.repository.OptionTradeRepository;
import com.marketmono.util.DateUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptionTradeAggregateService {

    private static final Set<String> BOUGHT_SIDE_MARKERS = Set.of("ASK", "ASK_SIDE", "BUY");
    private static final Set<String> SOLD_SIDE_MARKERS = Set.of("BID", "BID_SIDE", "SELL");
    private static final Set<String> AGGRESSIVE_BOUGHT_SIDE_MARKERS = Set.of("ABOVE_ASK");
    private static final Set<String> AGGRESSIVE_SOLD_SIDE_MARKERS = Set.of("BELOW_BID");
    private static final BigDecimal DEEP_ITM_THRESHOLD_RATIO = BigDecimal.valueOf(0.05);

    private final OptionTradeAggregateRepository aggregateRepository;
    private final OptionTradeAggregateMemberRepository memberRepository;
    private final OptionTradeRepository optionTradeRepository;

    @Transactional(readOnly = true, timeout = 5)
    public OptionTradeAggregateQueryResult getAggregates(
        OptionTradeAggregateFilter filter,
        Pageable pageable,
        OptionTradeCountMode countMode
    ) {
        return switch (countMode) {
            case EXACT -> {
                Page<OptionTradeAggregateDto> page = getAggregates(filter, pageable);
                yield OptionTradeAggregateQueryResult.exact(page.getContent(), page.getTotalElements(), page.hasNext());
            }
            case SLICE -> {
                Slice<OptionTradeAggregateDto> slice = getAggregateSlice(filter, pageable);
                yield OptionTradeAggregateQueryResult.slice(slice.getContent(), slice.hasNext());
            }
        };
    }

    @Transactional(readOnly = true, timeout = 5)
    public Page<OptionTradeAggregateDto> getAggregates(OptionTradeAggregateFilter filter, Pageable pageable) {
        Specification<OptionTradeAggregate> spec = buildSpecification(filter);
        Page<OptionTradeAggregate> aggregates = aggregateRepository.findAll(spec, pageable);
        List<OptionTradeAggregateDto> dtos = toDtosWithVolumeFallback(aggregates.getContent());
        return new PageImpl<>(dtos, pageable, aggregates.getTotalElements());
    }

    private Slice<OptionTradeAggregateDto> getAggregateSlice(
        OptionTradeAggregateFilter filter,
        Pageable pageable
    ) {
        Specification<OptionTradeAggregate> spec = buildSpecification(filter);

        Slice<OptionTradeAggregate> slice = aggregateRepository.findBy(spec, query -> {
            var fluentQuery = pageable.getSort().isSorted()
                ? query.sortBy(pageable.getSort())
                : query;
            return fluentQuery.slice(pageable);
        });

        List<OptionTradeAggregateDto> dtos = toDtosWithVolumeFallback(slice.getContent());
        return new org.springframework.data.domain.SliceImpl<>(dtos, pageable, slice.hasNext());
    }

    public List<OptionTradeAggregateDto> getRecentAggregates(int limit) {
        return getRecentAggregates(limit, null, null);
    }

    public List<OptionTradeAggregateDto> getRecentAggregates(int limit, Instant since, Instant until) {
        PageRequest pageable = PageRequest.of(
            0,
            Math.min(Math.max(1, limit), 2000),
            Sort.by(
                Sort.Order.desc("lastExecutedAt"),
                Sort.Order.desc("aggregateId")
            )
        );

        OptionTradeAggregateFilter filter = new OptionTradeAggregateFilter(
            null,
            null,
            since,
            until,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        Page<OptionTradeAggregate> aggregates = aggregateRepository.findAll(buildSpecification(filter), pageable);
        return toDtosWithVolumeFallback(aggregates.getContent());
    }

    public List<OptionTradeAggregateDto> hydrateVolumes(List<OptionTradeAggregateDto> aggregates) {
        if (aggregates.isEmpty()) {
            return List.of();
        }

        List<UUID> aggregateIdsNeedingFallback = aggregates.stream()
            .filter(aggregate -> aggregate.volume() == null)
            .map(OptionTradeAggregateDto::aggregateId)
            .toList();

        Map<UUID, Long> fallbackVolumes = loadFallbackVolumes(aggregateIdsNeedingFallback);

        return aggregates.stream()
            .map(aggregate -> withFallbackVolume(aggregate, fallbackVolumes.get(aggregate.aggregateId())))
            .toList();
    }

    public List<OptionTradeDto> getConstituents(UUID aggregateId) {
        List<OptionTradeAggregateMember> members = memberRepository.findByAggregateIdOrderByMemberOrderAsc(aggregateId);
        if (members.isEmpty()) {
            return List.of();
        }

        List<OptionTradeId> ids = members.stream()
            .map(member -> new OptionTradeId(member.getTradeId(), member.getExecutedAt()))
            .toList();

        Map<OptionTradeId, OptionTrade> tradesById = new HashMap<>();
        for (OptionTrade trade : optionTradeRepository.findAllById(ids)) {
            tradesById.put(trade.getId(), trade);
        }

        List<OptionTradeDto> orderedTrades = new ArrayList<>(members.size());
        for (OptionTradeAggregateMember member : members) {
            OptionTrade trade = tradesById.get(new OptionTradeId(member.getTradeId(), member.getExecutedAt()));
            if (trade != null) {
                orderedTrades.add(OptionTradeDto.from(trade));
            }
        }

        return orderedTrades;
    }

    private List<OptionTradeAggregateDto> toDtosWithVolumeFallback(List<OptionTradeAggregate> aggregates) {
        if (aggregates.isEmpty()) {
            return List.of();
        }

        return hydrateVolumes(aggregates.stream()
            .map(OptionTradeAggregateDto::from)
            .toList());
    }

    private Map<UUID, Long> loadFallbackVolumes(List<UUID> aggregateIdsNeedingFallback) {
        if (aggregateIdsNeedingFallback.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> fallbackByAggregateId = new HashMap<>();
        for (OptionTradeAggregateMemberRepository.AggregateFallbackVolume fallback
            : memberRepository.findFallbackVolumesByAggregateIdIn(aggregateIdsNeedingFallback)) {
            if (fallback.getAggregateId() == null || fallback.getVolume() == null) {
                continue;
            }

            fallbackByAggregateId.merge(fallback.getAggregateId(), fallback.getVolume(), Math::max);
        }

        return fallbackByAggregateId;
    }

    private OptionTradeAggregateDto withFallbackVolume(OptionTradeAggregateDto aggregate, Long fallbackVolume) {
        if (aggregate.volume() != null || fallbackVolume == null) {
            return aggregate;
        }

        return new OptionTradeAggregateDto(
            aggregate.aggregateId(),
            aggregate.groupingVersion(),
            aggregate.ticker(),
            aggregate.optionChain(),
            aggregate.side(),
            aggregate.aggressor(),
            aggregate.conditionSignature(),
            aggregate.isSweep(),
            aggregate.isFloorTrade(),
            aggregate.isMultileg(),
            aggregate.firstExecutedAt(),
            aggregate.lastExecutedAt(),
            aggregate.representativePrice(),
            aggregate.minPrice(),
            aggregate.maxPrice(),
            aggregate.totalSize(),
            aggregate.totalPremium(),
            aggregate.tradeCount(),
            aggregate.exchangeCount(),
            aggregate.expiry(),
            aggregate.optionType(),
            aggregate.strike(),
            aggregate.underlyingPriceFirst(),
            aggregate.underlyingPriceLast(),
            aggregate.openInterest(),
            fallbackVolume
        );
    }

    private Predicate matchesAny(
        CriteriaBuilder cb,
        Expression<String> side,
        Expression<String> aggressor,
        Set<String> values
    ) {
        CriteriaBuilder.In<String> sideIn = cb.in(side);
        values.forEach(sideIn::value);

        CriteriaBuilder.In<String> aggressorIn = cb.in(aggressor);
        values.forEach(aggressorIn::value);

        return cb.or(sideIn, aggressorIn);
    }

    private Expression<String> normalizedSide(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return cb.upper(root.get("side").as(String.class));
    }

    private Expression<String> normalizedAggressor(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return cb.upper(root.get("aggressor").as(String.class));
    }

    private Predicate crossPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return cb.like(cb.upper(root.get("conditionSignature").as(String.class)), "%CROSS%");
    }

    private Predicate aggregateAskPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        Expression<String> side = normalizedSide(root, cb);
        Expression<String> aggressor = normalizedAggressor(root, cb);
        Predicate aggressiveBuy = matchesAny(cb, side, aggressor, AGGRESSIVE_BOUGHT_SIDE_MARKERS);
        Predicate aggressiveSell = matchesAny(cb, side, aggressor, AGGRESSIVE_SOLD_SIDE_MARKERS);
        Predicate buy = matchesAny(cb, side, aggressor, BOUGHT_SIDE_MARKERS);
        return cb.and(cb.not(aggressiveBuy), cb.not(aggressiveSell), buy);
    }

    private Predicate aggregateBidPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        Expression<String> side = normalizedSide(root, cb);
        Expression<String> aggressor = normalizedAggressor(root, cb);
        Predicate aggressiveBuy = matchesAny(cb, side, aggressor, AGGRESSIVE_BOUGHT_SIDE_MARKERS);
        Predicate aggressiveSell = matchesAny(cb, side, aggressor, AGGRESSIVE_SOLD_SIDE_MARKERS);
        Predicate buy = matchesAny(cb, side, aggressor, BOUGHT_SIDE_MARKERS);
        Predicate sell = matchesAny(cb, side, aggressor, SOLD_SIDE_MARKERS);
        return cb.and(cb.not(aggressiveBuy), cb.not(aggressiveSell), cb.not(buy), sell);
    }

    private Predicate aggregateAboveAskPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return matchesAny(cb, normalizedSide(root, cb), normalizedAggressor(root, cb), AGGRESSIVE_BOUGHT_SIDE_MARKERS);
    }

    private Predicate aggregateBelowBidPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return matchesAny(cb, normalizedSide(root, cb), normalizedAggressor(root, cb), AGGRESSIVE_SOLD_SIDE_MARKERS);
    }

    private Predicate aggregateNoSidePredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        Expression<String> side = normalizedSide(root, cb);
        Expression<String> aggressor = normalizedAggressor(root, cb);
        Predicate aggressiveBuy = matchesAny(cb, side, aggressor, AGGRESSIVE_BOUGHT_SIDE_MARKERS);
        Predicate aggressiveSell = matchesAny(cb, side, aggressor, AGGRESSIVE_SOLD_SIDE_MARKERS);
        Predicate buy = matchesAny(cb, side, aggressor, BOUGHT_SIDE_MARKERS);
        Predicate sell = matchesAny(cb, side, aggressor, SOLD_SIDE_MARKERS);
        return cb.and(cb.not(aggressiveBuy), cb.not(aggressiveSell), cb.not(buy), cb.not(sell));
    }

    private Predicate comparableOptionDataPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("strike")),
            cb.notEqual(root.get("strike"), BigDecimal.ZERO),
            cb.isNotNull(root.get("optionType")),
            cb.or(
                cb.isNotNull(root.get("underlyingPriceLast")),
                cb.isNotNull(root.get("underlyingPriceFirst"))
            )
        );
    }

    private Expression<BigDecimal> underlyingPriceExpression(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        CriteriaBuilder.Coalesce<BigDecimal> underlying = cb.coalesce();
        underlying.value(root.get("underlyingPriceLast").as(BigDecimal.class));
        underlying.value(root.get("underlyingPriceFirst").as(BigDecimal.class));
        return underlying;
    }

    private Predicate itmPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        Expression<BigDecimal> underlying = underlyingPriceExpression(root, cb);
        Expression<BigDecimal> strike = root.get("strike").as(BigDecimal.class);
        Expression<String> optionType = cb.lower(root.get("optionType").as(String.class));

        Predicate callItm = cb.and(
            cb.equal(optionType, "call"),
            cb.greaterThanOrEqualTo(underlying, strike)
        );
        Predicate putItm = cb.and(
            cb.equal(optionType, "put"),
            cb.lessThanOrEqualTo(underlying, strike)
        );

        return cb.or(callItm, putItm);
    }

    private Predicate deepItmPredicate(Root<OptionTradeAggregate> root, CriteriaBuilder cb) {
        Expression<BigDecimal> underlying = underlyingPriceExpression(root, cb);
        Expression<BigDecimal> strike = root.get("strike").as(BigDecimal.class);
        Expression<Number> ratio = cb.quot(
            cb.abs(cb.diff(underlying, strike)),
            strike
        );

        return cb.and(
            comparableOptionDataPredicate(root, cb),
            itmPredicate(root, cb),
            cb.greaterThan(ratio.as(BigDecimal.class), DEEP_ITM_THRESHOLD_RATIO)
        );
    }

    Specification<OptionTradeAggregate> buildSpecification(OptionTradeAggregateFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.tickers() != null && !filter.tickers().isEmpty()) {
                predicates.add(root.get("ticker").in(filter.tickers()));
            }
            if (filter.ignoreTickers() != null && !filter.ignoreTickers().isEmpty()) {
                predicates.add(cb.not(root.get("ticker").in(filter.ignoreTickers())));
            }
            if (filter.since() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastExecutedAt"), filter.since()));
            }
            if (filter.until() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastExecutedAt"), filter.until()));
            }
            if (filter.expirations() != null && !filter.expirations().isEmpty()) {
                predicates.add(root.get("expiry").in(filter.expirations()));
            }
            if (filter.minExpiryDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiry"), filter.minExpiryDate()));
            }
            if (filter.maxExpiryDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiry"), filter.maxExpiryDate()));
            }
            if (filter.minPremium() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalPremium"), filter.minPremium()));
            }
            if (filter.maxPremium() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalPremium"), filter.maxPremium()));
            }
            if (filter.minSize() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalSize"), filter.minSize()));
            }
            if (filter.maxSize() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalSize"), filter.maxSize()));
            }
            if (filter.minVolume() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("volume"), filter.minVolume()));
            }
            if (filter.maxVolume() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("volume"), filter.maxVolume()));
            }
            if (filter.calls() != null && !filter.calls()) {
                predicates.add(cb.notEqual(cb.lower(root.get("optionType")), "call"));
            }
            if (filter.puts() != null && !filter.puts()) {
                predicates.add(cb.notEqual(cb.lower(root.get("optionType")), "put"));
            }
            if (Boolean.TRUE.equals(filter.sweepOnly())) {
                predicates.add(cb.isTrue(root.get("isSweep")));
            }
            if (Boolean.TRUE.equals(filter.floorOnly())) {
                predicates.add(cb.isTrue(root.get("isFloorTrade")));
            }
            if (filter.hasSweep() != null) {
                predicates.add(cb.equal(root.get("isSweep"), filter.hasSweep()));
            }
            if (filter.hasFloor() != null) {
                predicates.add(cb.equal(root.get("isFloorTrade"), filter.hasFloor()));
            }
            if (filter.hasSingleleg() != null) {
                predicates.add(cb.equal(root.get("isMultileg"), !filter.hasSingleleg()));
            }
            if (filter.hasMultileg() != null) {
                predicates.add(cb.equal(root.get("isMultileg"), filter.hasMultileg()));
            }
            if (filter.side() != null && !filter.side().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("side")), filter.side()));
            }
            if (filter.cross() != null && !filter.cross()) {
                predicates.add(cb.not(crossPredicate(root, cb)));
            }
            if (filter.regular() != null && !filter.regular()) {
                predicates.add(cb.or(
                    cb.isTrue(root.get("isSweep")),
                    cb.isTrue(root.get("isFloorTrade")),
                    crossPredicate(root, cb)
                ));
            }
            if (filter.bid() != null && !filter.bid()) {
                predicates.add(cb.not(aggregateBidPredicate(root, cb)));
            }
            if (filter.ask() != null && !filter.ask()) {
                predicates.add(cb.not(aggregateAskPredicate(root, cb)));
            }
            if (filter.noSide() != null && !filter.noSide()) {
                predicates.add(cb.not(aggregateNoSidePredicate(root, cb)));
            }
            if (filter.aboveAsk() != null && !filter.aboveAsk()) {
                predicates.add(cb.not(aggregateAboveAskPredicate(root, cb)));
            }
            if (filter.belowBid() != null && !filter.belowBid()) {
                predicates.add(cb.not(aggregateBelowBidPredicate(root, cb)));
            }
            if (Boolean.TRUE.equals(filter.volumeGtOi())) {
                predicates.add(cb.and(
                    cb.isNotNull(root.get("volume")),
                    cb.isNotNull(root.get("openInterest")),
                    cb.greaterThan(root.get("volume").as(Long.class), root.get("openInterest").as(Long.class))
                ));
            }
            if (Boolean.TRUE.equals(filter.sizeGtOi())) {
                predicates.add(cb.and(
                    cb.isNotNull(root.get("totalSize")),
                    cb.isNotNull(root.get("openInterest")),
                    cb.greaterThan(root.get("totalSize").as(Long.class), root.get("openInterest").as(Long.class))
                ));
            }
            if (Boolean.TRUE.equals(filter.hideExpired())) {
                predicates.add(cb.or(
                    cb.isNull(root.get("expiry")),
                    cb.greaterThanOrEqualTo(root.get("expiry"), DateUtils.getCurrentDateInEasternTime())
                ));
            }
            if (Boolean.TRUE.equals(filter.itmOnly())) {
                predicates.add(cb.and(
                    comparableOptionDataPredicate(root, cb),
                    itmPredicate(root, cb)
                ));
            }
            if (Boolean.TRUE.equals(filter.otmOnly())) {
                predicates.add(cb.and(
                    comparableOptionDataPredicate(root, cb),
                    cb.not(itmPredicate(root, cb))
                ));
            }
            if (Boolean.TRUE.equals(filter.excludeDeepItm())) {
                predicates.add(cb.not(deepItmPredicate(root, cb)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record OptionTradeAggregateFilter(
        List<String> tickers,
        List<String> ignoreTickers,
        Instant since,
        Instant until,
        List<LocalDate> expirations,
        LocalDate minExpiryDate,
        LocalDate maxExpiryDate,
        BigDecimal minPremium,
        BigDecimal maxPremium,
        Long minSize,
        Long maxSize,
        Long minVolume,
        Long maxVolume,
        Boolean calls,
        Boolean puts,
        Boolean sweepOnly,
        Boolean floorOnly,
        Boolean hasSweep,
        Boolean hasFloor,
        Boolean hasSingleleg,
        Boolean hasMultileg,
        String side,
        Boolean cross,
        Boolean regular,
        Boolean bid,
        Boolean ask,
        Boolean noSide,
        Boolean aboveAsk,
        Boolean belowBid,
        Boolean volumeGtOi,
        Boolean sizeGtOi,
        Boolean excludeDeepItm,
        Boolean otmOnly,
        Boolean itmOnly,
        Boolean hideExpired
    ) implements FlowFilterDescriptor {}

    public record OptionTradeAggregateQueryResult(
        List<OptionTradeAggregateDto> results,
        Long totalCount,
        boolean hasNext,
        boolean totalCountExact,
        OptionTradeCountMode countMode
    ) {
        public static OptionTradeAggregateQueryResult exact(
            List<OptionTradeAggregateDto> results,
            long totalCount,
            boolean hasNext
        ) {
            return new OptionTradeAggregateQueryResult(
                results,
                totalCount,
                hasNext,
                true,
                OptionTradeCountMode.EXACT
            );
        }

        public static OptionTradeAggregateQueryResult slice(
            List<OptionTradeAggregateDto> results,
            boolean hasNext
        ) {
            return new OptionTradeAggregateQueryResult(
                results,
                null,
                hasNext,
                false,
                OptionTradeCountMode.SLICE
            );
        }
    }
}
