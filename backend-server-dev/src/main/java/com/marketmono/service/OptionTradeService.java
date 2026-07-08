package com.marketmono.service;

import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.OptionTrade;
import com.marketmono.repository.OptionTradeRepository;
import com.marketmono.util.DateUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptionTradeService {

    private static final BigDecimal DEEP_ITM_THRESHOLD_RATIO = BigDecimal.valueOf(0.05);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MIDPOINT_MULTIPLIER = BigDecimal.valueOf(0.5);

    private final OptionTradeRepository optionTradeRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true, timeout = 5)
    public Page<OptionTradeDto> getTrades(OptionTradeFilter filter, Pageable pageable) {
        Specification<OptionTrade> spec = buildSpecification(filter);
        return optionTradeRepository.findAll(spec, pageable).map(OptionTradeDto::from);
    }

    @Transactional(readOnly = true, timeout = 5)
    public OptionTradeQueryResult getTrades(
        OptionTradeFilter filter,
        Pageable pageable,
        OptionTradeCountMode countMode
    ) {
        return switch (countMode) {
            case EXACT -> {
                Page<OptionTradeDto> page = getTrades(filter, pageable);
                yield OptionTradeQueryResult.exact(page.getContent(), page.getTotalElements(), page.hasNext());
            }
            case SLICE -> {
                Slice<OptionTradeDto> slice = getTradeSlice(filter, pageable);
                yield OptionTradeQueryResult.slice(slice.getContent(), slice.hasNext());
            }
        };
    }

    @Transactional(readOnly = true, timeout = 5)
    public Page<OptionTradeDto> getTradesByTicker(String ticker, Pageable pageable) {
        return optionTradeRepository.findByTickerOrderByExecutedAtDesc(ticker, pageable)
            .map(OptionTradeDto::from);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Map<String, Object>> getTickerStats(Instant since, int limit) {
        return jdbcTemplate.queryForList("""
            SELECT ticker,
                   COUNT(*) AS trade_count,
                   COALESCE(SUM(size), 0) AS total_volume,
                   COALESCE(SUM(premium), 0) AS total_premium,
                   COUNT(*) FILTER (WHERE is_sweep) AS sweep_count,
                   COUNT(*) FILTER (WHERE is_floor_trade) AS floor_count
            FROM option_trades
            WHERE executed_at >= ?
            GROUP BY ticker
            ORDER BY total_premium DESC
            LIMIT ?
            """, OffsetDateTime.ofInstant(since, ZoneOffset.UTC), limit);
    }

    @Transactional(readOnly = true, timeout = 5)
    public Map<String, Object> getAggregateStats(Instant since) {
        return jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS trade_count,
                   COALESCE(SUM(size), 0) AS total_volume,
                   COALESCE(SUM(premium), 0) AS total_premium,
                   COUNT(*) FILTER (WHERE is_sweep) AS sweep_count,
                   COUNT(*) FILTER (WHERE is_floor_trade) AS floor_count,
                   COUNT(*) FILTER (WHERE is_multileg) AS multileg_count
            FROM option_trades
            WHERE executed_at >= ?
            """, OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
    }

    private Slice<OptionTradeDto> getTradeSlice(OptionTradeFilter filter, Pageable pageable) {
        Specification<OptionTrade> spec = buildSpecification(filter);

        Slice<OptionTrade> slice = optionTradeRepository.findBy(spec, query -> {
            var fluentQuery = pageable.getSort().isSorted()
                ? query.sortBy(pageable.getSort())
                : query;
            return fluentQuery.slice(pageable);
        });

        return slice.map(OptionTradeDto::from);
    }

    static boolean hasContradictoryMultilegFilters(Boolean hasSingleleg, Boolean hasMultileg) {
        if (hasSingleleg == null || hasMultileg == null) {
            return false;
        }

        return !Boolean.valueOf(!hasSingleleg).equals(hasMultileg);
    }

    static Boolean resolveRequestedMultileg(Boolean hasSingleleg, Boolean hasMultileg) {
        if (hasSingleleg != null) {
            return !hasSingleleg;
        }

        return hasMultileg;
    }

    private Specification<OptionTrade> buildSpecification(OptionTradeFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.tickers() != null && !filter.tickers().isEmpty()) {
                predicates.add(root.get("ticker").in(filter.tickers()));
            }
            if (filter.ignoreTickers() != null && !filter.ignoreTickers().isEmpty()) {
                predicates.add(cb.not(root.get("ticker").in(filter.ignoreTickers())));
            }
            if (filter.since() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), filter.since()));
            }
            if (filter.until() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), filter.until()));
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
                predicates.add(cb.greaterThanOrEqualTo(root.get("premium"), filter.minPremium()));
            }
            if (filter.maxPremium() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("premium"), filter.maxPremium()));
            }
            if (filter.minSize() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("size").as(Long.class), filter.minSize()));
            }
            if (filter.maxSize() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("size").as(Long.class), filter.maxSize()));
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
            if (hasContradictoryMultilegFilters(filter.hasSingleleg(), filter.hasMultileg())) {
                log.warn(
                    "Contradictory option-trade multileg filters received: hasSingleleg={}, hasMultileg={}",
                    filter.hasSingleleg(),
                    filter.hasMultileg()
                );
                return cb.disjunction();
            }
            Boolean requestedMultileg = resolveRequestedMultileg(filter.hasSingleleg(), filter.hasMultileg());
            if (requestedMultileg != null) {
                predicates.add(cb.equal(root.get("isMultileg"), requestedMultileg));
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
            if (filter.side() != null && !filter.side().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("side").as(String.class)), filter.side()));
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
                predicates.add(cb.not(rawBidPredicate(root, cb)));
            }
            if (filter.mid() != null && !filter.mid()) {
                predicates.add(cb.not(rawMidPredicate(root, cb)));
            }
            if (filter.ask() != null && !filter.ask()) {
                predicates.add(cb.not(rawAskPredicate(root, cb)));
            }
            if (filter.noSide() != null && !filter.noSide()) {
                predicates.add(cb.not(rawNoSidePredicate(root, cb)));
            }
            if (filter.aboveAsk() != null && !filter.aboveAsk()) {
                predicates.add(cb.not(rawAboveAskPredicate(root, cb)));
            }
            if (filter.belowBid() != null && !filter.belowBid()) {
                predicates.add(cb.not(rawBelowBidPredicate(root, cb)));
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
                    cb.isNotNull(root.get("size")),
                    cb.isNotNull(root.get("openInterest")),
                    cb.greaterThan(root.get("size").as(Long.class), root.get("openInterest").as(Long.class))
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

    private Predicate crossPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.like(cb.upper(root.get("condition").as(String.class)), "%CROSS%");
    }

    private Predicate comparableOptionDataPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("strike")),
            cb.notEqual(root.get("strike"), ZERO),
            cb.isNotNull(root.get("optionType")),
            cb.isNotNull(root.get("underlyingPrice"))
        );
    }

    private Predicate itmPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> underlying = root.get("underlyingPrice").as(BigDecimal.class);
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

    private Predicate deepItmPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> underlying = root.get("underlyingPrice").as(BigDecimal.class);
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

    private Predicate pricePresentPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("price")),
            cb.notEqual(root.get("price"), ZERO)
        );
    }

    private Predicate bidPresentPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("nbboBid")),
            cb.notEqual(root.get("nbboBid"), ZERO)
        );
    }

    private Predicate askPresentPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            cb.isNotNull(root.get("nbboAsk")),
            cb.notEqual(root.get("nbboAsk"), ZERO)
        );
    }

    private Predicate comparableNbboPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            pricePresentPredicate(root, cb),
            bidPresentPredicate(root, cb),
            askPresentPredicate(root, cb)
        );
    }

    private Predicate distinctNbboPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        return cb.and(
            comparableNbboPredicate(root, cb),
            cb.notEqual(root.get("nbboBid"), root.get("nbboAsk"))
        );
    }

    private Expression<BigDecimal> midPriceExpression(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> bid = root.get("nbboBid").as(BigDecimal.class);
        Expression<BigDecimal> ask = root.get("nbboAsk").as(BigDecimal.class);
        return cb.prod(cb.sum(bid, ask), MIDPOINT_MULTIPLIER);
    }

    private Predicate rawAskPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get("price").as(BigDecimal.class);
        Expression<BigDecimal> ask = root.get("nbboAsk").as(BigDecimal.class);
        Expression<BigDecimal> mid = midPriceExpression(root, cb);

        return cb.and(
            distinctNbboPredicate(root, cb),
            cb.greaterThan(price, mid),
            cb.lessThanOrEqualTo(price, ask)
        );
    }

    private Predicate rawBidPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get("price").as(BigDecimal.class);
        Expression<BigDecimal> bid = root.get("nbboBid").as(BigDecimal.class);
        Expression<BigDecimal> mid = midPriceExpression(root, cb);

        return cb.and(
            distinctNbboPredicate(root, cb),
            cb.lessThan(price, mid),
            cb.greaterThanOrEqualTo(price, bid)
        );
    }

    private Predicate rawAboveAskPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get("price").as(BigDecimal.class);
        Expression<BigDecimal> ask = root.get("nbboAsk").as(BigDecimal.class);

        return cb.and(
            distinctNbboPredicate(root, cb),
            cb.greaterThan(price, ask)
        );
    }

    private Predicate rawBelowBidPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get("price").as(BigDecimal.class);
        Expression<BigDecimal> bid = root.get("nbboBid").as(BigDecimal.class);

        return cb.and(
            distinctNbboPredicate(root, cb),
            cb.lessThan(price, bid)
        );
    }

    private Predicate rawMidPredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get("price").as(BigDecimal.class);
        Expression<BigDecimal> mid = midPriceExpression(root, cb);

        return cb.and(
            distinctNbboPredicate(root, cb),
            cb.equal(price, mid)
        );
    }

    private Predicate rawNoSidePredicate(Root<OptionTrade> root, CriteriaBuilder cb) {
        Predicate ask = rawAskPredicate(root, cb);
        Predicate bid = rawBidPredicate(root, cb);
        Predicate mid = rawMidPredicate(root, cb);
        Predicate aboveAsk = rawAboveAskPredicate(root, cb);
        Predicate belowBid = rawBelowBidPredicate(root, cb);
        Predicate pricePresent = pricePresentPredicate(root, cb);
        Predicate bidPresent = bidPresentPredicate(root, cb);
        Predicate askPresent = askPresentPredicate(root, cb);
        Predicate equalNbbo = cb.and(
            bidPresent,
            askPresent,
            cb.equal(root.get("nbboBid"), root.get("nbboAsk"))
        );

        return cb.or(
            cb.not(pricePresent),
            cb.not(cb.and(bidPresent, askPresent)),
            equalNbbo,
            cb.not(cb.or(ask, bid, mid, aboveAsk, belowBid))
        );
    }

    @Getter
    @Accessors(fluent = true)
    @Builder
    public static final class OptionTradeFilter implements FlowFilterDescriptor {

        private final List<String> tickers;
        private final List<String> ignoreTickers;
        private final Instant since;
        private final Instant until;
        private final List<LocalDate> expirations;
        private final LocalDate minExpiryDate;
        private final LocalDate maxExpiryDate;
        private final BigDecimal minPremium;
        private final BigDecimal maxPremium;
        private final Long minSize;
        private final Long maxSize;
        private final Long minVolume;
        private final Long maxVolume;
        private final Boolean calls;
        private final Boolean puts;
        private final Boolean sweepOnly;
        private final Boolean floorOnly;
        private final Boolean hasSweep;
        private final Boolean hasFloor;
        private final Boolean hasSingleleg;
        private final Boolean hasMultileg;
        private final String side;
        private final Boolean cross;
        private final Boolean regular;
        private final Boolean bid;
        private final Boolean mid;
        private final Boolean ask;
        private final Boolean noSide;
        private final Boolean aboveAsk;
        private final Boolean belowBid;
        private final Boolean volumeGtOi;
        private final Boolean sizeGtOi;
        private final Boolean excludeDeepItm;
        private final Boolean otmOnly;
        private final Boolean itmOnly;
        private final Boolean hideExpired;
    }

    public record OptionTradeQueryResult(
        List<OptionTradeDto> results,
        Long totalCount,
        boolean hasNext,
        boolean totalCountExact,
        OptionTradeCountMode countMode
    ) {

        public static OptionTradeQueryResult exact(List<OptionTradeDto> results, long totalCount, boolean hasNext) {
            return new OptionTradeQueryResult(results, totalCount, hasNext, true, OptionTradeCountMode.EXACT);
        }

        public static OptionTradeQueryResult slice(List<OptionTradeDto> results, boolean hasNext) {
            return new OptionTradeQueryResult(results, null, hasNext, false, OptionTradeCountMode.SLICE);
        }
    }
}
