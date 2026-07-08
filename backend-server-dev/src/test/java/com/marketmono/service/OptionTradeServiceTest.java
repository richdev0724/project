package com.marketmono.service;

import com.marketmono.dto.OptionTradeCountMode;
import com.marketmono.entity.OptionTrade;
import com.marketmono.repository.OptionTradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeServiceTest {

    @Mock
    private OptionTradeRepository optionTradeRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OptionTradeService service;

    @Test
    void shouldResolveSinglelegOnlyToMultilegFalse() {
        assertThat(OptionTradeService.resolveRequestedMultileg(true, null)).isFalse();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(true, null)).isFalse();
    }

    @Test
    void shouldResolveMultilegOnlyToRequestedValue() {
        assertThat(OptionTradeService.resolveRequestedMultileg(null, true)).isTrue();
        assertThat(OptionTradeService.resolveRequestedMultileg(null, false)).isFalse();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(null, true)).isFalse();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(null, false)).isFalse();
    }

    @Test
    void shouldTreatConsistentPairsAsSinglePredicateRequest() {
        assertThat(OptionTradeService.resolveRequestedMultileg(true, false)).isFalse();
        assertThat(OptionTradeService.resolveRequestedMultileg(false, true)).isTrue();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(true, false)).isFalse();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(false, true)).isFalse();
    }

    @Test
    void shouldDetectContradictoryPairs() {
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(true, true)).isTrue();
        assertThat(OptionTradeService.hasContradictoryMultilegFilters(false, false)).isTrue();
    }

    @Test
    void shouldReturnExactPageMetadataWhenCountModeIsExact() {
        Pageable pageable = PageRequest.of(0, 2);
        OptionTrade trade = trade();
        Page<OptionTrade> page = new PageImpl<>(List.of(trade), pageable, 5);

        when(optionTradeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        var result = service.getTrades(filter(), pageable, OptionTradeCountMode.EXACT);

        assertThat(result.results()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(5L);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalCountExact()).isTrue();
        assertThat(result.countMode()).isEqualTo(OptionTradeCountMode.EXACT);

        verify(optionTradeRepository).findAll(any(Specification.class), eq(pageable));
        verify(optionTradeRepository, never()).findBy(any(Specification.class), any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldReturnSliceMetadataWithoutExactCountWhenCountModeIsSlice() {
        Pageable pageable = PageRequest.of(0, 2);
        Slice<OptionTrade> slice = new SliceImpl<>(List.of(trade(), trade()), pageable, true);

        when(optionTradeRepository.findBy(any(Specification.class), any())).thenAnswer(invocation -> {
            Function<JpaSpecificationExecutor.SpecificationFluentQuery<OptionTrade>, ?> queryFunction = invocation.getArgument(1);
            JpaSpecificationExecutor.SpecificationFluentQuery<OptionTrade> fluentQuery =
                mock(JpaSpecificationExecutor.SpecificationFluentQuery.class, RETURNS_SELF);

            when(fluentQuery.slice(pageable)).thenReturn((Slice) slice);

            return queryFunction.apply(fluentQuery);
        });

        var result = service.getTrades(filter(), pageable, OptionTradeCountMode.SLICE);

        assertThat(result.results()).hasSize(2);
        assertThat(result.totalCount()).isNull();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalCountExact()).isFalse();
        assertThat(result.countMode()).isEqualTo(OptionTradeCountMode.SLICE);

        verify(optionTradeRepository).findBy(any(Specification.class), any());
        verify(optionTradeRepository, never()).findAll(any(Specification.class), eq(pageable));
    }

    private static OptionTradeService.OptionTradeFilter filter() {
        return OptionTradeService.OptionTradeFilter.builder()
            .tickers(List.of("SPY"))
            .since(Instant.parse("2026-03-18T13:00:00Z"))
            .minPremium(new BigDecimal("50000"))
            .minVolume(100L)
            .hasSingleleg(true)
            .hasMultileg(false)
            .build();
    }

    private static OptionTrade trade() {
        return OptionTrade.builder()
            .tradeId(UUID.randomUUID())
            .executedAt(Instant.parse("2026-03-18T14:00:00Z"))
            .ticker("SPY")
            .optionChain("SPY_260320_C_510")
            .price(new BigDecimal("1.00"))
            .size(10)
            .premium(new BigDecimal("1000.00"))
            .underlyingPrice(new BigDecimal("500.00"))
            .nbboBid(new BigDecimal("0.95"))
            .nbboAsk(new BigDecimal("1.05"))
            .exchange("CBOE")
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
