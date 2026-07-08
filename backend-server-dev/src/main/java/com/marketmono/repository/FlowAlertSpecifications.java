package com.marketmono.repository;

import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTypeEnum;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for FlowAlert entity filtering.
 * Provides type-safe, dynamic query building to avoid PostgreSQL parameter type inference issues.
 */
public class FlowAlertSpecifications {

    // Entity field constants to avoid duplication
    private static final String TICKER_FIELD = "ticker";
    private static final String EXPIRY_DATE_FIELD = "expiryDate";
    private static final String TOTAL_PREMIUM_FIELD = "totalPremium";
    private static final String TOTAL_SIZE_FIELD = "totalSize";
    private static final String VOLUME_FIELD = "volume";
    private static final String OPEN_INTEREST_FIELD = "openInterest";
    private static final String MARKET_CAP_FIELD = "marketCap";
    private static final String OPTION_TYPE_FIELD = "optionType";
    private static final String PRICE_FIELD = "price";
    private static final String BID_FIELD = "bid";
    private static final String ASK_FIELD = "ask";
    private static final String STRIKE_FIELD = "strike";
    private static final String UNDERLYING_PRICE_FIELD = "underlyingPrice";
    private static final String HAS_SWEEP_FIELD = "hasSweep";
    private static final String HAS_FLOOR_FIELD = "hasFloor";
    private static final String HAS_SINGLELEG_FIELD = "hasSingleleg";
    private static final String HAS_MULTILEG_FIELD = "hasMultileg";
    private static final String ALL_OPENING_TRADES_FIELD = "allOpeningTrades";

    // Option type constants
    private static final OptionTypeEnum CALL_OPTION = OptionTypeEnum.CALL;
    private static final OptionTypeEnum PUT_OPTION = OptionTypeEnum.PUT;

    /**
     * Filter criteria for FlowAlert queries.
     */
    public record FlowAlertFilter(
            List<String> tickers,
            List<String> ignoreTickers,
            LocalDate minExpiryDate,
            LocalDate maxExpiryDate,
            List<LocalDate> expirationDates,
            BigDecimal minPremium,
            BigDecimal maxPremium,
            Integer minSize,
            Integer maxSize,
            Integer minVolume,
            Integer maxVolume,
            Long minMarketCap,
            Long maxMarketCap,
            String optionType,
            Boolean calls,
            Boolean puts,
            Boolean sweepsOnly,
            Boolean floorsOnly,
            Boolean hasSweep,
            Boolean hasFloor,
            Boolean regular,
            Boolean bid,
            Boolean mid,
            Boolean ask,
            Boolean noSide,
            Boolean aboveAsk,
            Boolean belowBid,
            Boolean hasSingleleg,
            Boolean hasMultileg,
            Boolean allOpeningTrades,
            Boolean otmOnly,
            Boolean itmOnly,
            Boolean excludeDeepItm,
            Boolean sizeGreaterOi,
            Boolean volumeGreaterOi
    ) {}

    /**
     * Builds a composite specification from all provided filter criteria.
     * Only non-null parameters are added to the query, avoiding parameter type issues.
     */
    public static Specification<FlowAlert> buildSpecification(FlowAlertFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = buildPredicates(filter, root, cb);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static List<Predicate> buildPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        addTickerPredicates(filter, root, cb, predicates);
        addDatePredicates(filter, root, cb, predicates);
        addNumericPredicates(filter, root, cb, predicates);
        addOptionTypePredicates(filter, root, cb, predicates);
        addSpecialPredicates(filter, root, cb, predicates);
        addSidePredicates(filter, root, cb, predicates);
        addDerivedPredicates(filter, root, cb, predicates);
        addBooleanFlagPredicates(filter, root, cb, predicates);

        return predicates;
    }

    private static void addTickerPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.tickers != null && !filter.tickers.isEmpty()) {
            predicates.add(root.get(TICKER_FIELD).in(filter.tickers));
        }
        if (filter.ignoreTickers != null && !filter.ignoreTickers.isEmpty()) {
            predicates.add(cb.not(root.get(TICKER_FIELD).in(filter.ignoreTickers)));
        }
    }

    private static void addDatePredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.minExpiryDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(EXPIRY_DATE_FIELD), filter.minExpiryDate));
        }
        if (filter.maxExpiryDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(EXPIRY_DATE_FIELD), filter.maxExpiryDate));
        }
        if (filter.expirationDates != null && !filter.expirationDates.isEmpty()) {
            predicates.add(root.get(EXPIRY_DATE_FIELD).in(filter.expirationDates));
        }
    }

    private static void addNumericPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.minPremium != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(TOTAL_PREMIUM_FIELD), filter.minPremium));
        }
        if (filter.maxPremium != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(TOTAL_PREMIUM_FIELD), filter.maxPremium));
        }

        if (filter.minSize != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(TOTAL_SIZE_FIELD), filter.minSize));
        }
        if (filter.maxSize != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(TOTAL_SIZE_FIELD), filter.maxSize));
        }

        if (filter.minVolume != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(VOLUME_FIELD), filter.minVolume));
        }
        if (filter.maxVolume != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(VOLUME_FIELD), filter.maxVolume));
        }

        if (filter.minMarketCap != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(MARKET_CAP_FIELD), filter.minMarketCap));
        }
        if (filter.maxMarketCap != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(MARKET_CAP_FIELD), filter.maxMarketCap));
        }
    }

    private static void addOptionTypePredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.optionType != null) {
            try {
                predicates.add(cb.equal(
                    root.get(OPTION_TYPE_FIELD),
                    OptionTypeEnum.valueOf(filter.optionType.toUpperCase())
                ));
            } catch (IllegalArgumentException ignored) {
                // Invalid option type string - skip filter
            }
        }
        if (filter.calls != null && !filter.calls) {
            predicates.add(cb.notEqual(root.get(OPTION_TYPE_FIELD), CALL_OPTION));
        }
        if (filter.puts != null && !filter.puts) {
            predicates.add(cb.notEqual(root.get(OPTION_TYPE_FIELD), PUT_OPTION));
        }
    }

    private static void addSpecialPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.sweepsOnly != null && filter.sweepsOnly) {
            predicates.add(cb.isTrue(root.get(HAS_SWEEP_FIELD)));
        }
        if (filter.floorsOnly != null && filter.floorsOnly) {
            predicates.add(cb.isTrue(root.get(HAS_FLOOR_FIELD)));
        }
        if (Boolean.FALSE.equals(filter.regular)) {
            predicates.add(cb.or(
                cb.isTrue(root.get(HAS_SWEEP_FIELD)),
                cb.isTrue(root.get(HAS_FLOOR_FIELD))
            ));
        }
    }

    private static void addBooleanFlagPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (filter.hasSweep != null) {
            predicates.add(cb.equal(root.get(HAS_SWEEP_FIELD), filter.hasSweep));
        }
        if (filter.hasFloor != null) {
            predicates.add(cb.equal(root.get(HAS_FLOOR_FIELD), filter.hasFloor));
        }
        if (filter.hasSingleleg != null) {
            predicates.add(cb.equal(root.get(HAS_SINGLELEG_FIELD), filter.hasSingleleg));
        }
        if (filter.hasMultileg != null) {
            predicates.add(cb.equal(root.get(HAS_MULTILEG_FIELD), filter.hasMultileg));
        }
        if (filter.allOpeningTrades != null) {
            predicates.add(cb.equal(root.get(ALL_OPENING_TRADES_FIELD), filter.allOpeningTrades));
        }
    }

    private static void addDerivedPredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (Boolean.TRUE.equals(filter.sizeGreaterOi)) {
            predicates.add(cb.greaterThan(root.get(TOTAL_SIZE_FIELD), root.get(OPEN_INTEREST_FIELD)));
        }
        if (Boolean.TRUE.equals(filter.volumeGreaterOi)) {
            predicates.add(cb.greaterThan(root.get(VOLUME_FIELD), root.get(OPEN_INTEREST_FIELD)));
        }
        if (Boolean.TRUE.equals(filter.otmOnly)) {
            predicates.add(buildUiOtmPredicate(root, cb));
        }
        if (Boolean.TRUE.equals(filter.itmOnly)) {
            predicates.add(cb.not(buildUiOtmPredicate(root, cb)));
        }
        if (Boolean.TRUE.equals(filter.excludeDeepItm)) {
            predicates.add(cb.not(buildDeepItmPredicate(root, cb)));
        }
    }

    private static void addSidePredicates(FlowAlertFilter filter, Root<FlowAlert> root, CriteriaBuilder cb, List<Predicate> predicates) {
        if (Boolean.FALSE.equals(filter.bid)) {
            predicates.add(cb.not(buildBidSidePredicate(root, cb)));
        }
        if (Boolean.FALSE.equals(filter.mid)) {
            predicates.add(cb.not(buildMidSidePredicate(root, cb)));
        }
        if (Boolean.FALSE.equals(filter.ask)) {
            predicates.add(cb.not(buildAskSidePredicate(root, cb)));
        }
        if (Boolean.FALSE.equals(filter.noSide)) {
            predicates.add(cb.not(buildNoSidePredicate(root, cb)));
        }
        if (Boolean.FALSE.equals(filter.aboveAsk)) {
            predicates.add(cb.not(buildAboveAskSidePredicate(root, cb)));
        }
        if (Boolean.FALSE.equals(filter.belowBid)) {
            predicates.add(cb.not(buildBelowBidSidePredicate(root, cb)));
        }
    }

    private static Predicate buildUiOtmPredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        Expression<BigDecimal> strike = root.get(STRIKE_FIELD);
        Expression<BigDecimal> underlying = root.get(UNDERLYING_PRICE_FIELD);
        return cb.or(
            cb.and(cb.equal(root.get(OPTION_TYPE_FIELD), PUT_OPTION), cb.greaterThan(underlying, strike)),
            cb.and(cb.equal(root.get(OPTION_TYPE_FIELD), CALL_OPTION), cb.greaterThan(strike, underlying))
        );
    }

    private static Predicate buildDeepItmPredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        Expression<BigDecimal> strike = root.get(STRIKE_FIELD);
        Expression<BigDecimal> underlying = root.get(UNDERLYING_PRICE_FIELD);
        Expression<BigDecimal> strikeTimes105 = cb.prod(strike, new BigDecimal("1.05"));
        Expression<BigDecimal> strikeTimes095 = cb.prod(strike, new BigDecimal("0.95"));
        return cb.or(
            cb.and(cb.equal(root.get(OPTION_TYPE_FIELD), CALL_OPTION), cb.greaterThan(underlying, strikeTimes105)),
            cb.and(cb.equal(root.get(OPTION_TYPE_FIELD), PUT_OPTION), cb.lessThan(underlying, strikeTimes095))
        );
    }

    private static Predicate buildBidSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get(PRICE_FIELD);
        Expression<BigDecimal> bid = root.get(BID_FIELD);
        return cb.and(
            hasFullQuote(root, cb),
            cb.lessThan(cb.prod(price, BigDecimal.valueOf(2)), buildBidAskSumExpression(root, cb)),
            cb.greaterThanOrEqualTo(price, bid)
        );
    }

    private static Predicate buildMidSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        return cb.and(
            hasFullQuote(root, cb),
            cb.equal(
                cb.prod(root.get(PRICE_FIELD), BigDecimal.valueOf(2)),
                buildBidAskSumExpression(root, cb)
            )
        );
    }

    private static Predicate buildAskSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        Expression<BigDecimal> price = root.get(PRICE_FIELD);
        Expression<BigDecimal> ask = root.get(ASK_FIELD);
        return cb.and(
            hasFullQuote(root, cb),
            cb.greaterThan(cb.prod(price, BigDecimal.valueOf(2)), buildBidAskSumExpression(root, cb)),
            cb.lessThanOrEqualTo(price, ask)
        );
    }

    private static Predicate buildNoSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        Predicate noQuoteSide = cb.or(
            cb.and(hasNonZero(root.get(BID_FIELD), cb), hasNonZero(root.get(ASK_FIELD), cb), cb.equal(root.get(BID_FIELD), root.get(ASK_FIELD))),
            cb.and(
                hasNonZero(root.get(PRICE_FIELD), cb),
                cb.or(
                    cb.isNull(root.get(BID_FIELD)),
                    cb.equal(root.get(BID_FIELD), BigDecimal.ZERO),
                    cb.isNull(root.get(ASK_FIELD)),
                    cb.equal(root.get(ASK_FIELD), BigDecimal.ZERO)
                )
            )
        );
        Predicate knownSide = cb.or(
            buildBidSidePredicate(root, cb),
            buildMidSidePredicate(root, cb),
            buildAskSidePredicate(root, cb),
            buildAboveAskSidePredicate(root, cb),
            buildBelowBidSidePredicate(root, cb)
        );
        return cb.or(noQuoteSide, cb.and(hasFullQuote(root, cb), cb.not(knownSide)));
    }

    private static Predicate buildAboveAskSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        return cb.and(hasFullQuote(root, cb), cb.greaterThan(root.get(PRICE_FIELD), root.get(ASK_FIELD)));
    }

    private static Predicate buildBelowBidSidePredicate(Root<FlowAlert> root, CriteriaBuilder cb) {
        return cb.and(hasFullQuote(root, cb), cb.lessThan(root.get(PRICE_FIELD), root.get(BID_FIELD)));
    }

    private static Expression<BigDecimal> buildBidAskSumExpression(Root<FlowAlert> root, CriteriaBuilder cb) {
        return cb.sum(root.get(BID_FIELD), root.get(ASK_FIELD));
    }

    private static Predicate hasFullQuote(Root<FlowAlert> root, CriteriaBuilder cb) {
        return cb.and(
            hasNonZero(root.get(PRICE_FIELD), cb),
            hasNonZero(root.get(BID_FIELD), cb),
            hasNonZero(root.get(ASK_FIELD), cb)
        );
    }

    private static Predicate hasNonZero(Expression<BigDecimal> value, CriteriaBuilder cb) {
        return cb.and(cb.isNotNull(value), cb.notEqual(value, BigDecimal.ZERO));
    }
}
