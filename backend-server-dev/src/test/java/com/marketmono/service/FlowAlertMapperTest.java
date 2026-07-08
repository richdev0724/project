package com.marketmono.service;

import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.SectorRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FlowAlertMapperTest {

    private final FlowAlertMapper mapper = new FlowAlertMapper(mock(SectorRepository.class));

    @Test
    void shouldUseCreatedAtWhenRestAlertOmitsStartTime() {
        Instant createdAt = Instant.parse("2026-06-25T14:31:12Z");
        UnusualWhalesFlowAlertDto dto = alert(createdAt, null);

        FlowAlert entity = mapper.mapToEntity(dto, DataSourceEnum.REST);

        assertThat(entity.getTradeStartTime()).isEqualTo(createdAt);
    }

    @Test
    void shouldRejectAlertWithoutStableTradeTimestamp() {
        UnusualWhalesFlowAlertDto dto = alert(null, null);

        assertThatThrownBy(() -> mapper.mapToEntity(dto, DataSourceEnum.REST))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing both start_time and created_at");
    }

    private static UnusualWhalesFlowAlertDto alert(Instant createdAt, Long startTime) {
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
            startTime,
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
}
