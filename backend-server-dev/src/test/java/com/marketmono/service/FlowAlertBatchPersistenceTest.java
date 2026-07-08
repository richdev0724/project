package com.marketmono.service;

import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAlertBatchPersistenceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FlowAlertBatchPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new FlowAlertBatchPersistence(jdbcTemplate);
    }

    @Test
    void shouldInsertBatchWithConflictIgnoreAndReportSkippedDuplicates() {
        FlowAlert inserted = sampleAlert(UUID.randomUUID(), Instant.parse("2026-05-16T14:30:00Z"));
        FlowAlert duplicate = sampleAlert(UUID.randomUUID(), Instant.parse("2026-05-16T14:31:00Z"));
        List<FlowAlert> alerts = List.of(inserted, duplicate);
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));
        when(jdbcTemplate.batchUpdate(anyString(), eq(alerts), eq(500), any()))
            .thenReturn(new int[][]{{1}, {0}});

        FlowAlertBatchPersistence.FlowAlertBatchResult result = persistence.persistBatch(alerts);

        assertThat(result.insertedAlerts()).containsExactly(inserted);
        assertThat(result.duplicateCount()).isEqualTo(1);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), eq(alerts), eq(500), any());
        assertThat(sqlCaptor.getValue())
            .contains("INSERT INTO flow_alerts")
            .contains("ON CONFLICT (alert_id, trade_start_time) DO NOTHING");
    }

    @Test
    void shouldClaimAlertIdsBeforeInsertingFlowRows() {
        FlowAlert inserted = sampleAlert(UUID.randomUUID(), Instant.parse("2026-05-16T14:30:00Z"));
        FlowAlert duplicate = sampleAlert(UUID.randomUUID(), Instant.parse("2026-05-16T14:31:00Z"));
        List<FlowAlert> alerts = List.of(inserted, duplicate);
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), any(), any(), any()))
            .thenAnswer(invocation -> {
                UUID alertId = invocation.getArgument(2);
                if (alertId.equals(inserted.getAlertId())) {
                    return alertId;
                }
                throw new EmptyResultDataAccessException(1);
            });
        when(jdbcTemplate.batchUpdate(anyString(), eq(List.of(inserted)), eq(500), any()))
            .thenReturn(new int[][]{{1}});

        FlowAlertBatchPersistence.FlowAlertBatchResult result = persistence.persistBatch(alerts);

        assertThat(result.insertedAlerts()).containsExactly(inserted);
        assertThat(result.duplicateCount()).isEqualTo(1);

        ArgumentCaptor<String> claimSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(claimSqlCaptor.capture(), eq(UUID.class), any(), any(), any());
        assertThat(claimSqlCaptor.getValue())
            .contains("INSERT INTO flow_alert_ingest_keys")
            .contains("ON CONFLICT (alert_id) DO NOTHING")
            .contains("RETURNING alert_id");

        ArgumentCaptor<String> insertSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(insertSqlCaptor.capture(), eq(List.of(inserted)), eq(500), any());
        assertThat(insertSqlCaptor.getValue())
            .contains("INSERT INTO flow_alerts")
            .contains("ON CONFLICT (alert_id, trade_start_time) DO NOTHING");
    }

    @Test
    void shouldSkipJdbcCallForEmptyBatch() {
        FlowAlertBatchPersistence.FlowAlertBatchResult result = persistence.persistBatch(List.of());

        assertThat(result.insertedAlerts()).isEmpty();
        assertThat(result.duplicateCount()).isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(), eq(500), any());
    }

    @Test
    void shouldCleanupLedgerEntriesOlderThanCutoff() {
        Instant cutoff = Instant.parse("2026-05-25T00:00:00Z");
        when(jdbcTemplate.update(anyString(), any(java.sql.Timestamp.class))).thenReturn(12);

        int deleted = persistence.cleanupIngestKeysBefore(cutoff);

        assertThat(deleted).isEqualTo(12);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> timestampCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), timestampCaptor.capture());
        assertThat(sqlCaptor.getValue())
            .contains("DELETE FROM flow_alert_ingest_keys")
            .contains("created_at < ?");
        assertThat(timestampCaptor.getValue()).isInstanceOf(java.sql.Timestamp.class);
    }

    private FlowAlert sampleAlert(UUID id, Instant tradeStartTime) {
        return FlowAlert.builder()
            .alertId(id)
            .ticker("SPY")
            .optionSymbol("SPY260516C00500000")
            .strike(new BigDecimal("500.00"))
            .expiryDate(LocalDate.of(2026, 5, 16))
            .optionType(OptionTypeEnum.CALL)
            .price(new BigDecimal("1.23"))
            .bid(new BigDecimal("1.20"))
            .ask(new BigDecimal("1.25"))
            .underlyingPrice(new BigDecimal("501.00"))
            .totalSize(10)
            .totalPremium(new BigDecimal("1230.00"))
            .tradeCount(1)
            .volume(100)
            .openInterest(1000)
            .hasSweep(false)
            .hasFloor(false)
            .hasSingleleg(true)
            .hasMultileg(false)
            .allOpeningTrades(false)
            .tradeStartTime(tradeStartTime)
            .dataSource(DataSourceEnum.WEBSOCKET)
            .build();
    }
}
