package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.client.UnusualWhalesWebSocketClient;
import com.marketmono.config.UnusualWhalesConfig;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTypeEnum;
import com.marketmono.repository.FlowAlertRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowAlertServiceTest {

    @Mock
    private FlowAlertRepository flowAlertRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private UnusualWhalesWebSocketClient webSocketClient;

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Mock
    private FlowAlertBroadcaster flowAlertBroadcaster;

    @Mock
    private FlowAlertMapper flowAlertMapper;

    @Mock
    private BatchedFlowAlertPersistence batchedPersistence;

    @Mock
    private FlowAlertBatchPersistence flowAlertBatchPersistence;

    @Mock
    private FlowAlertAuditService auditService;

    @Mock
    private ObjectProvider<MeterRegistry> meterRegistryProvider;

    private UnusualWhalesConfig config;
    private SimpleMeterRegistry meterRegistry;
    private FlowAlertService service;

    @BeforeEach
    void setUp() {
        config = new UnusualWhalesConfig();
        config.getApi().setApiKey("test-key");
        config.getFlow().setPrimarySource("WEBSOCKET");
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(meterRegistryProvider.getIfAvailable()).thenReturn(meterRegistry);
        service = new FlowAlertService(
            flowAlertRepository,
            unusualWhalesClient,
            webSocketClient,
            config,
            connectionManager,
            flowAlertBroadcaster,
            flowAlertMapper,
            batchedPersistence,
            flowAlertBatchPersistence,
            auditService,
            meterRegistryProvider
        );
    }

    @Test
    void shouldUseLockedRestPollerWithoutWebSocketSkip() {
        when(unusualWhalesClient.getLockedFlowAlerts())
            .thenReturn(new UnusualWhalesFlowResponse(List.of(), null, null));

        service.syncFlowAlerts();

        verify(unusualWhalesClient).getLockedFlowAlerts();
        verify(unusualWhalesClient, never()).getRecentFlowAlerts();
        verifyNoInteractions(connectionManager);
    }

    @Test
    void shouldSkipInvalidRestAlertsWithoutFailingPoll() {
        UnusualWhalesFlowAlertDto dto = alertWithoutTimestamp();
        when(unusualWhalesClient.getLockedFlowAlerts())
            .thenReturn(new UnusualWhalesFlowResponse(List.of(dto), null, null));
        when(flowAlertMapper.mapToEntity(dto, DataSourceEnum.REST))
            .thenThrow(new IllegalArgumentException("Flow alert " + dto.id() + " missing both start_time and created_at"));

        assertThatCode(service::syncFlowAlerts).doesNotThrowAnyException();

        verify(flowAlertMapper).mapToEntity(dto, DataSourceEnum.REST);
        verify(flowAlertBatchPersistence, never()).persistBatch(any());
    }

    @Test
    void shouldRecordLockedRestPollMetrics() {
        UnusualWhalesFlowAlertDto dto = alertWithCreatedAt();
        FlowAlert entity = sampleEntity();
        when(unusualWhalesClient.getLockedFlowAlerts())
            .thenReturn(new UnusualWhalesFlowResponse(List.of(dto), null, null));
        when(flowAlertMapper.mapToEntity(dto, DataSourceEnum.REST)).thenReturn(entity);
        when(flowAlertBatchPersistence.persistBatch(List.of(entity)))
            .thenReturn(new FlowAlertBatchPersistence.FlowAlertBatchResult(List.of(entity), 0));

        service.syncFlowAlerts();

        assertThat(meterRegistry.get("flow_alerts.rest.poll.attempts")
            .tag("source", "rest-locked")
            .tag("outcome", "success")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("flow_alerts.rest.poll.rows")
            .tag("source", "rest-locked")
            .tag("result", "fetched")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("flow_alerts.rest.poll.rows")
            .tag("source", "rest-locked")
            .tag("result", "inserted")
            .counter()
            .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("flow_alerts.rest.poll.duration")
            .tag("source", "rest-locked")
            .tag("outcome", "success")
            .timer()
            .count()).isEqualTo(1L);
    }

    @Test
    void shouldSkipLockedRestPollWhenApiKeyMissing() {
        config.getApi().setApiKey("  ");

        service.syncFlowAlerts();

        verifyNoInteractions(unusualWhalesClient);
        assertThat(meterRegistry.get("flow_alerts.rest.poll.attempts")
            .tag("source", "rest-locked")
            .tag("outcome", "disabled")
            .counter()
            .count()).isEqualTo(1.0);
    }

    private static UnusualWhalesFlowAlertDto alertWithoutTimestamp() {
        return new UnusualWhalesFlowAlertDto(
            UUID.randomUUID().toString(),
            "CRWV",
            "CRWV270617P00110000",
            new BigDecimal("110.00"),
            LocalDate.of(2027, 6, 17),
            "put",
            new BigDecimal("35.90"),
            new BigDecimal("35.70"),
            new BigDecimal("36.50"),
            new BigDecimal("106.11"),
            500,
            new BigDecimal("1795000.00"),
            1,
            500,
            202,
            new BigDecimal("2.47"),
            null,
            null,
            "RepeatedHits",
            false,
            false,
            true,
            false,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "rule-1",
            1,
            null,
            "Stock",
            false
        );
    }

    private static UnusualWhalesFlowAlertDto alertWithCreatedAt() {
        Instant createdAt = Instant.parse("2026-06-25T14:31:12Z");
        return new UnusualWhalesFlowAlertDto(
            UUID.randomUUID().toString(),
            "CRWV",
            "CRWV270617P00110000",
            new BigDecimal("110.00"),
            LocalDate.of(2027, 6, 17),
            "put",
            new BigDecimal("35.90"),
            new BigDecimal("35.70"),
            new BigDecimal("36.50"),
            new BigDecimal("106.11"),
            500,
            new BigDecimal("1795000.00"),
            1,
            500,
            202,
            new BigDecimal("2.47"),
            null,
            null,
            "RepeatedHits",
            false,
            false,
            true,
            false,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            createdAt,
            "rule-1",
            1,
            null,
            "Stock",
            false
        );
    }

    private static FlowAlert sampleEntity() {
        return FlowAlert.builder()
            .alertId(UUID.randomUUID())
            .ticker("CRWV")
            .optionSymbol("CRWV270617P00110000")
            .strike(new BigDecimal("110.00"))
            .expiryDate(LocalDate.of(2027, 6, 17))
            .optionType(OptionTypeEnum.PUT)
            .tradeStartTime(Instant.parse("2026-06-25T14:31:12Z"))
            .dataSource(DataSourceEnum.REST)
            .build();
    }
}
