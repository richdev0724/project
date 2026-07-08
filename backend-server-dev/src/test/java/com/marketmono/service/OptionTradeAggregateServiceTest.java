package com.marketmono.service;

import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.OptionTrade;
import com.marketmono.entity.OptionTradeAggregate;
import com.marketmono.entity.OptionTradeAggregateMember;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.marketmono.repository.OptionTradeAggregateMemberRepository;
import com.marketmono.repository.OptionTradeAggregateRepository;
import com.marketmono.repository.OptionTradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeAggregateServiceTest {

    @Mock
    private OptionTradeAggregateRepository aggregateRepository;

    @Mock
    private OptionTradeAggregateMemberRepository memberRepository;

    @Mock
    private OptionTradeRepository optionTradeRepository;

    @InjectMocks
    private OptionTradeAggregateService service;

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnExactAggregateResultWhenExactCountModeRequested() {
        var aggregate = aggregate(UUID.randomUUID(), 2400L);
        var pageable = PageRequest.of(0, 25);
        when(aggregateRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(aggregate), pageable, 1));

        var result = service.getAggregates(emptyFilter(), pageable, OptionTradeCountMode.EXACT);

        assertThat(result.results()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.totalCountExact()).isTrue();
        assertThat(result.countMode()).isEqualTo(OptionTradeCountMode.EXACT);
        verify(aggregateRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReturnSliceAggregateResultWithoutExactCountQuery() {
        var aggregate = aggregate(UUID.randomUUID(), 2400L);
        var pageable = PageRequest.of(0, 25);
        when(aggregateRepository.findBy(any(Specification.class), any(Function.class)))
            .thenReturn(new SliceImpl<>(List.of(aggregate), pageable, true));

        var result = service.getAggregates(emptyFilter(), pageable, OptionTradeCountMode.SLICE);

        assertThat(result.results()).hasSize(1);
        assertThat(result.totalCount()).isNull();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalCountExact()).isFalse();
        assertThat(result.countMode()).isEqualTo(OptionTradeCountMode.SLICE);
        verify(aggregateRepository).findBy(any(Specification.class), any(Function.class));
        verify(aggregateRepository, never()).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldReturnConstituentsInMemberOrder() {
        UUID aggregateId = UUID.randomUUID();
        UUID firstTradeId = UUID.randomUUID();
        UUID secondTradeId = UUID.randomUUID();
        Instant firstExecutedAt = Instant.parse("2026-03-18T14:00:00Z");
        Instant secondExecutedAt = Instant.parse("2026-03-18T14:00:01Z");

        when(memberRepository.findByAggregateIdOrderByMemberOrderAsc(aggregateId)).thenReturn(List.of(
            member(aggregateId, firstTradeId, firstExecutedAt, 0),
            member(aggregateId, secondTradeId, secondExecutedAt, 1)
        ));
        when(optionTradeRepository.findAllById(any())).thenReturn(List.of(
            trade(secondTradeId, secondExecutedAt, new BigDecimal("1.10"), "NYSE"),
            trade(firstTradeId, firstExecutedAt, new BigDecimal("1.00"), "CBOE")
        ));

        List<OptionTradeDto> result = service.getConstituents(aggregateId);

        assertThat(result).extracting(OptionTradeDto::tradeId)
            .containsExactly(firstTradeId, secondTradeId);
        assertThat(result).extracting(OptionTradeDto::exchange)
            .containsExactly("CBOE", "NYSE");
    }

    @Test
    void shouldReturnEmptyConstituentsWhenAggregateHasNoMembers() {
        UUID aggregateId = UUID.randomUUID();
        when(memberRepository.findByAggregateIdOrderByMemberOrderAsc(aggregateId)).thenReturn(List.of());

        List<OptionTradeDto> result = service.getConstituents(aggregateId);

        assertThat(result).isEmpty();
        verifyNoInteractions(optionTradeRepository);
    }

    @Test
    void shouldBackfillAggregateVolumeFromConstituentTradesWhenMissing() {
        UUID aggregateId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        Instant executedAt = Instant.parse("2026-03-18T14:00:00Z");

        OptionTradeAggregate aggregate = OptionTradeAggregate.builder()
            .aggregateId(aggregateId)
            .groupingVersion(1)
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .side("ASK_SIDE")
            .aggressor("ASK_SIDE")
            .conditionSignature("AUTO")
            .isSweep(true)
            .isFloorTrade(false)
            .isMultileg(false)
            .firstExecutedAt(executedAt)
            .lastExecutedAt(executedAt.plusSeconds(1))
            .representativePrice(new BigDecimal("1.0100"))
            .minPrice(new BigDecimal("1.0000"))
            .maxPrice(new BigDecimal("1.0200"))
            .totalSize(30L)
            .totalPremium(new BigDecimal("3000.00"))
            .tradeCount(2)
            .exchangeCount(2)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .underlyingPriceFirst(new BigDecimal("500.00"))
            .underlyingPriceLast(new BigDecimal("500.05"))
            .openInterest(1500)
            .volume(null)
            .build();

        when(aggregateRepository.findAll(any(Specification.class), eq(PageRequest.of(
            0,
            25,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("lastExecutedAt"),
                org.springframework.data.domain.Sort.Order.desc("aggregateId")
            )
        )))).thenReturn(new PageImpl<>(List.of(aggregate)));
        when(memberRepository.findFallbackVolumesByAggregateIdIn(List.of(aggregateId))).thenReturn(List.of(
            fallbackVolume(aggregateId, 800L)
        ));

        var result = service.getRecentAggregates(25);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().volume()).isEqualTo(800L);
        verify(optionTradeRepository, never()).findByTradeIdIn(any());
    }

    @Test
    void shouldHydrateBufferedAggregateDtosWhenVolumeIsMissingWithoutTradeIdOnlyLookup() {
        UUID aggregateId = UUID.randomUUID();

        OptionTradeAggregateDto dto = aggregateDto(aggregateId, null);

        when(memberRepository.findFallbackVolumesByAggregateIdIn(List.of(aggregateId))).thenReturn(List.of(
            fallbackVolume(aggregateId, 800L)
        ));

        List<OptionTradeAggregateDto> result = service.hydrateVolumes(List.of(dto));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().volume()).isEqualTo(800L);
        verify(optionTradeRepository, never()).findByTradeIdIn(any());
    }

    @Test
    void shouldUseHighestCompositeFallbackVolumeWhenTradeIdRepeatsAcrossTimestamps() {
        UUID aggregateId = UUID.randomUUID();
        OptionTradeAggregateDto dto = aggregateDto(aggregateId, null);

        when(memberRepository.findFallbackVolumesByAggregateIdIn(List.of(aggregateId))).thenReturn(List.of(
            fallbackVolume(aggregateId, 200L),
            fallbackVolume(aggregateId, 800L)
        ));

        List<OptionTradeAggregateDto> result = service.hydrateVolumes(List.of(dto));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().volume()).isEqualTo(800L);
        verify(optionTradeRepository, never()).findByTradeIdIn(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildExpandedSpecificationForSupportedFilters() {
        Instant since = Instant.parse("2026-03-18T13:00:00Z");
        Instant until = Instant.parse("2026-03-18T14:00:00Z");
        LocalDate expiry = LocalDate.of(2026, 3, 20);
        LocalDate minExpiry = LocalDate.of(2026, 3, 19);
        LocalDate maxExpiry = LocalDate.of(2026, 4, 17);

        var filter = new OptionTradeAggregateService.OptionTradeAggregateFilter(
            List.of("SPY"),
            List.of("QQQ"),
            since,
            until,
            List.of(expiry),
            minExpiry,
            maxExpiry,
            new BigDecimal("1000.00"),
            new BigDecimal("5000.00"),
            10L,
            50L,
            100L,
            1000L,
            false,
            null,
            true,
            null,
            false,
            null,
            true,
            null,
            "ASK_SIDE",
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

        var spec = service.buildSpecification(filter);

        Root<?> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> tickerPath = mock(Path.class);
        Path<Instant> lastExecutedAtPath = mock(Path.class);
        Path<LocalDate> expiryPath = mock(Path.class);
        Path<BigDecimal> totalPremiumPath = mock(Path.class);
        Path<Long> totalSizePath = mock(Path.class);
        Path<Long> volumePath = mock(Path.class);
        Path<String> optionTypePath = mock(Path.class);
        Path<Boolean> isSweepPath = mock(Path.class);
        Path<Boolean> isMultilegPath = mock(Path.class);
        Path<String> sidePath = mock(Path.class);
        Expression<String> loweredOptionTypePath = mock(Expression.class);
        Expression<String> upperSidePath = mock(Expression.class);
        Predicate combined = mock(Predicate.class);

        Predicate includeTicker = mock(Predicate.class);
        Predicate ignoreTicker = mock(Predicate.class);
        Predicate notIgnoreTicker = mock(Predicate.class);
        Predicate sincePredicate = mock(Predicate.class);
        Predicate untilPredicate = mock(Predicate.class);
        Predicate expiryListPredicate = mock(Predicate.class);
        Predicate minExpiryPredicate = mock(Predicate.class);
        Predicate maxExpiryPredicate = mock(Predicate.class);
        Predicate minPremiumPredicate = mock(Predicate.class);
        Predicate maxPremiumPredicate = mock(Predicate.class);
        Predicate minSizePredicate = mock(Predicate.class);
        Predicate maxSizePredicate = mock(Predicate.class);
        Predicate minVolumePredicate = mock(Predicate.class);
        Predicate maxVolumePredicate = mock(Predicate.class);
        Predicate notCallPredicate = mock(Predicate.class);
        Predicate sweepOnlyPredicate = mock(Predicate.class);
        Predicate noSweepPredicate = mock(Predicate.class);
        Predicate singleLegPredicate = mock(Predicate.class);
        Predicate sidePredicate = mock(Predicate.class);

        when(root.get("ticker")).thenReturn((Path) tickerPath);
        when(root.get("lastExecutedAt")).thenReturn((Path) lastExecutedAtPath);
        when(root.get("expiry")).thenReturn((Path) expiryPath);
        when(root.get("totalPremium")).thenReturn((Path) totalPremiumPath);
        when(root.get("totalSize")).thenReturn((Path) totalSizePath);
        when(root.get("volume")).thenReturn((Path) volumePath);
        when(root.get("optionType")).thenReturn((Path) optionTypePath);
        when(root.get("isSweep")).thenReturn((Path) isSweepPath);
        when(root.get("isMultileg")).thenReturn((Path) isMultilegPath);
        when(root.get("side")).thenReturn((Path) sidePath);

        when(tickerPath.in(List.of("SPY"))).thenReturn(includeTicker);
        when(tickerPath.in(List.of("QQQ"))).thenReturn(ignoreTicker);
        when(cb.not(ignoreTicker)).thenReturn(notIgnoreTicker);
        when(cb.greaterThanOrEqualTo(lastExecutedAtPath, since)).thenReturn(sincePredicate);
        when(cb.lessThanOrEqualTo(lastExecutedAtPath, until)).thenReturn(untilPredicate);
        when(expiryPath.in(List.of(expiry))).thenReturn(expiryListPredicate);
        when(cb.greaterThanOrEqualTo(expiryPath, minExpiry)).thenReturn(minExpiryPredicate);
        when(cb.lessThanOrEqualTo(expiryPath, maxExpiry)).thenReturn(maxExpiryPredicate);
        when(cb.greaterThanOrEqualTo(totalPremiumPath, new BigDecimal("1000.00"))).thenReturn(minPremiumPredicate);
        when(cb.lessThanOrEqualTo(totalPremiumPath, new BigDecimal("5000.00"))).thenReturn(maxPremiumPredicate);
        when(cb.greaterThanOrEqualTo(totalSizePath, 10L)).thenReturn(minSizePredicate);
        when(cb.lessThanOrEqualTo(totalSizePath, 50L)).thenReturn(maxSizePredicate);
        when(cb.greaterThanOrEqualTo(volumePath, 100L)).thenReturn(minVolumePredicate);
        when(cb.lessThanOrEqualTo(volumePath, 1000L)).thenReturn(maxVolumePredicate);
        when(cb.lower(optionTypePath)).thenReturn(loweredOptionTypePath);
        when(cb.notEqual(loweredOptionTypePath, "call")).thenReturn(notCallPredicate);
        when(cb.isTrue(isSweepPath)).thenReturn(sweepOnlyPredicate);
        when(cb.equal(isSweepPath, false)).thenReturn(noSweepPredicate);
        when(cb.equal(isMultilegPath, false)).thenReturn(singleLegPredicate);
        when(cb.upper(sidePath)).thenReturn(upperSidePath);
        when(cb.equal(upperSidePath, "ASK_SIDE")).thenReturn(sidePredicate);
        when(cb.and(any(Predicate[].class))).thenReturn(combined);

        Predicate result = spec.toPredicate((Root) root, query, cb);

        assertThat(result).isSameAs(combined);
        verify(tickerPath).in(List.of("SPY"));
        verify(tickerPath).in(List.of("QQQ"));
        verify(cb).not(ignoreTicker);
        verify(cb).greaterThanOrEqualTo(lastExecutedAtPath, since);
        verify(cb).lessThanOrEqualTo(lastExecutedAtPath, until);
        verify(expiryPath).in(List.of(expiry));
        verify(cb).greaterThanOrEqualTo(expiryPath, minExpiry);
        verify(cb).lessThanOrEqualTo(expiryPath, maxExpiry);
        verify(cb).greaterThanOrEqualTo(totalPremiumPath, new BigDecimal("1000.00"));
        verify(cb).lessThanOrEqualTo(totalPremiumPath, new BigDecimal("5000.00"));
        verify(cb).greaterThanOrEqualTo(totalSizePath, 10L);
        verify(cb).lessThanOrEqualTo(totalSizePath, 50L);
        verify(cb).greaterThanOrEqualTo(volumePath, 100L);
        verify(cb).lessThanOrEqualTo(volumePath, 1000L);
        verify(cb).lower(optionTypePath);
        verify(cb).notEqual(loweredOptionTypePath, "call");
        verify(cb).isTrue(isSweepPath);
        verify(cb).equal(isSweepPath, false);
        verify(cb).equal(isMultilegPath, false);
        verify(cb).upper(sidePath);
        verify(cb).equal(upperSidePath, "ASK_SIDE");
    }

    private static OptionTradeAggregateService.OptionTradeAggregateFilter emptyFilter() {
        return new OptionTradeAggregateService.OptionTradeAggregateFilter(
            null,
            null,
            Instant.parse("2026-05-15T13:30:00Z"),
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
            null,
            null
        );
    }

    private static OptionTradeAggregate aggregate(UUID aggregateId, Long volume) {
        return OptionTradeAggregate.builder()
            .aggregateId(aggregateId)
            .groupingVersion(1)
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .side("ASK_SIDE")
            .aggressor("ASK_SIDE")
            .conditionSignature("AUTO")
            .isSweep(true)
            .isFloorTrade(false)
            .isMultileg(false)
            .firstExecutedAt(Instant.parse("2026-03-18T14:00:00Z"))
            .lastExecutedAt(Instant.parse("2026-03-18T14:00:01Z"))
            .representativePrice(new BigDecimal("1.0100"))
            .minPrice(new BigDecimal("1.0000"))
            .maxPrice(new BigDecimal("1.0200"))
            .totalSize(30L)
            .totalPremium(new BigDecimal("3000.00"))
            .tradeCount(2)
            .exchangeCount(2)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .strike(new BigDecimal("510.00"))
            .underlyingPriceFirst(new BigDecimal("500.00"))
            .underlyingPriceLast(new BigDecimal("500.05"))
            .openInterest(1500)
            .volume(volume)
            .build();
    }

    private static OptionTradeAggregateMember member(UUID aggregateId, UUID tradeId, Instant executedAt, int order) {
        return OptionTradeAggregateMember.builder()
            .aggregateId(aggregateId)
            .tradeId(tradeId)
            .executedAt(executedAt)
            .memberOrder(order)
            .build();
    }

    private static OptionTradeAggregateDto aggregateDto(UUID aggregateId, Long volume) {
        return new OptionTradeAggregateDto(
            aggregateId,
            1,
            "SPY",
            "SPY_260320_C_510",
            "ASK_SIDE",
            "ASK_SIDE",
            "AUTO",
            true,
            false,
            false,
            Instant.parse("2026-03-18T14:00:00Z"),
            Instant.parse("2026-03-18T14:00:01Z"),
            new BigDecimal("1.0100"),
            new BigDecimal("1.0000"),
            new BigDecimal("1.0200"),
            30L,
            new BigDecimal("3000.00"),
            2,
            2,
            LocalDate.of(2026, 3, 20),
            "call",
            new BigDecimal("510.00"),
            new BigDecimal("500.00"),
            new BigDecimal("500.05"),
            1500,
            volume
        );
    }

    private record FallbackVolume(UUID aggregateId, Long volume)
        implements OptionTradeAggregateMemberRepository.AggregateFallbackVolume {

        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public Long getVolume() {
            return volume;
        }
    }

    private static OptionTradeAggregateMemberRepository.AggregateFallbackVolume fallbackVolume(
        UUID aggregateId,
        Long volume
    ) {
        return new FallbackVolume(aggregateId, volume);
    }

    private static OptionTrade trade(UUID tradeId, Instant executedAt, BigDecimal price, String exchange) {
        return OptionTrade.builder()
            .tradeId(tradeId)
            .executedAt(executedAt)
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .price(price)
            .size(10)
            .premium(new BigDecimal("1000.00"))
            .underlyingPrice(new BigDecimal("500.00"))
            .nbboBid(new BigDecimal("0.95"))
            .nbboAsk(new BigDecimal("1.05"))
            .exchange(exchange)
            .side("BUY")
            .condition("AUTO")
            .aggressor("BUY")
            .isSweep(false)
            .isFloorTrade(false)
            .isMultileg(false)
            .expiry(LocalDate.of(2026, 3, 20))
            .optionType("call")
            .openInterest(1200)
            .strike(new BigDecimal("510.00"))
            .volume(800L)
            .build();
    }
}
