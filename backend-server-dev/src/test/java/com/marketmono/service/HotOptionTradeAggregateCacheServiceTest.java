package com.marketmono.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.marketmono.dto.OptionTradeAggregateDto;
import com.marketmono.dto.OptionTradeAggregatePageResponse;
import com.marketmono.util.DateUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HotOptionTradeAggregateCacheServiceTest {

    private final HotOptionTradeAggregateCacheService service = new HotOptionTradeAggregateCacheService();

    @Test
    void shouldThrottleDefaultFlowPresetRefreshSignals() {
        assertThat(service.shouldRefreshDefaultFlowPreset()).isTrue();
        assertThat(service.shouldRefreshDefaultFlowPreset()).isFalse();

        service.markDefaultFlowPresetRefreshFailed();

        assertThat(service.shouldRefreshDefaultFlowPreset()).isTrue();
    }

    @Test
    void shouldExposeDefaultFlowPresetKeyAndFilter() {
        var filter = service.defaultFlowFilter();

        assertThat(service.defaultFlowPresetKey().page()).isEqualTo(1);
        assertThat(service.defaultFlowPresetKey().pageSize()).isEqualTo(150);
        assertThat(service.defaultFlowPresetKey().minPremium())
            .isEqualByComparingTo(HotOptionTradeAggregateCacheService.defaultFlowPremium());
        assertThat(filter.minPremium())
            .isEqualByComparingTo(HotOptionTradeAggregateCacheService.defaultFlowPremium());
        assertThat(filter.since()).isEqualTo(DateUtils.startOfCurrentEasternDay());
        assertThat(filter.until()).isNull();
        assertThat(filter.tickers()).isNull();
    }

    @Test
    void shouldReadDefaultPresetFromSecondLineRedisWhenLocalCacheMisses() throws Exception {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        OptionTradeAggregateDto dto = new OptionTradeAggregateDto(
            UUID.randomUUID(),
            1,
            "SPY",
            "SPY_260417_C_500",
            "ASK_SIDE",
            "ASK_SIDE",
            "AUTO",
            false,
            false,
            false,
            Instant.parse("2026-04-17T14:00:00Z"),
            Instant.parse("2026-04-17T14:00:01Z"),
            new BigDecimal("1.01"),
            new BigDecimal("1.00"),
            new BigDecimal("1.02"),
            10L,
            new BigDecimal("60000.00"),
            1,
            1,
            LocalDate.of(2026, 4, 17),
            "call",
            new BigDecimal("500.00"),
            new BigDecimal("499.50"),
            new BigDecimal("500.10"),
            1000,
            2000L
        );
        OptionTradeAggregatePageResponse response = new OptionTradeAggregatePageResponse(
            List.of(dto),
            1L,
            false,
            true,
            com.marketmono.dto.OptionTradeCountMode.EXACT
        );
        String serialized = JsonMapper.builder().findAndAddModules().build().writeValueAsString(response);
        when(valueOperations.get(anyString())).thenReturn(serialized);

        HotOptionTradeAggregateCacheService redisBackedService = new HotOptionTradeAggregateCacheService(
            stringRedisTemplate,
            JsonMapper.builder().findAndAddModules().build(),
            null,
            true
        );

        OptionTradeAggregatePageResponse restored = redisBackedService.getIfPresent(
            redisBackedService.defaultFlowPresetKey()
        );

        assertThat(restored).isNotNull();
        assertThat(restored.results()).containsExactly(dto);
        assertThat(restored.totalCount()).isEqualTo(1L);
    }
}
