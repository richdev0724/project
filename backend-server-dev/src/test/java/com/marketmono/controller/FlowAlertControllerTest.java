package com.marketmono.controller;

import com.marketmono.dto.FlowAlertPageResponse;
import com.marketmono.dto.FlowAlertResponseDto;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.FlowAlertSpecifications.FlowAlertFilter;
import com.marketmono.service.FlowAlertMapperService;
import com.marketmono.service.FlowAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAlertControllerTest {

    @Mock
    private FlowAlertService flowAlertService;

    @Mock
    private FlowAlertMapperService mapperService;

    @InjectMocks
    private FlowAlertController controller;

    @Test
    void shouldUseUncappedPaginationForUnfilteredFlowAlerts() {
        when(flowAlertService.getFlowAlerts(any())).thenReturn(new PageImpl<>(List.of()));
        when(mapperService.toDtoList(List.of())).thenReturn(List.of());

        ResponseEntity<FlowAlertPageResponse> response = invokeFlowAlertQuery(
            2,
            1000,
            null,
            null,
            null,
            null
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(flowAlertService).getFlowAlerts(pageableCaptor.capture());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().results()).isEmpty();
        assertThat(response.getBody().totalCount()).isZero();

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(1000);
        assertThat(pageable.getSort().getOrderFor("tradeStartTime")).isNotNull();
    }

    @Test
    void shouldPreserveFlowAlertCsvEmptyTokenBehavior() {
        when(flowAlertService.getFlowAlertsWithFilters(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(mapperService.toDtoList(List.of())).thenReturn(List.of());

        invokeFlowAlertQuery(
            1,
            100,
            " spy, , qqq ",
            " ,dia",
            " mid, ,MEGA ",
            new BigDecimal("50000")
        );

        ArgumentCaptor<FlowAlertFilter> filterCaptor = ArgumentCaptor.forClass(FlowAlertFilter.class);
        verify(flowAlertService).getFlowAlertsWithFilters(filterCaptor.capture(), any());

        FlowAlertFilter filter = filterCaptor.getValue();
        assertThat(filter.tickers()).containsExactly("SPY", "", "QQQ");
        assertThat(filter.ignoreTickers()).containsExactly("", "DIA");
        assertThat(filter.minMarketCap()).isEqualTo(2_000_000_000L);
        assertThat(filter.maxMarketCap()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldTreatCommaOnlyFlowAlertCsvAsEmptyFilterList() {
        when(flowAlertService.getFlowAlertsWithFilters(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(mapperService.toDtoList(List.of())).thenReturn(List.of());

        invokeFlowAlertQuery(
            1,
            100,
            ",",
            null,
            null,
            new BigDecimal("50000")
        );

        ArgumentCaptor<FlowAlertFilter> filterCaptor = ArgumentCaptor.forClass(FlowAlertFilter.class);
        verify(flowAlertService).getFlowAlertsWithFilters(filterCaptor.capture(), any());

        assertThat(filterCaptor.getValue().tickers()).isEmpty();
    }

    private ResponseEntity<FlowAlertPageResponse> invokeFlowAlertQuery(
        int page,
        int pageSize,
        String tickers,
        String ignoreTickers,
        String marketCap,
        BigDecimal minimumPremium
    ) {
        return controller.getFlowAlerts(
            page,
            pageSize,
            tickers,
            ignoreTickers,
            marketCap,
            null,
            null,
            minimumPremium,
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

    @SuppressWarnings("unused")
    private static FlowAlertResponseDto dto() {
        return new FlowAlertResponseDto("idx", "id", null, null, null);
    }
}
