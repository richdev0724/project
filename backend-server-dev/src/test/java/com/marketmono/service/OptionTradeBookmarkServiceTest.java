package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.dto.UnusualWhalesFlowResponse;
import com.marketmono.entity.BookmarkSourceType;
import com.marketmono.entity.OptionTradeBookmark;
import com.marketmono.repository.FlowAlertRepository;
import com.marketmono.repository.OptionTradeBookmarkRepository;
import com.marketmono.repository.OptionTradeRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionTradeBookmarkServiceTest {

    @Mock
    private OptionTradeBookmarkRepository bookmarkRepository;

    @Mock
    private OptionTradeRepository optionTradeRepository;

    @Mock
    private FlowAlertRepository flowAlertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private OptionTradeBookmarkService service;

    @Test
    void shouldResolveUnpersistedFlowAlertBookmarksFromRecentUpstreamFeed() {
        UUID userId = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();

        when(bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
            OptionTradeBookmark.builder()
                .tradeId(alertId.toString())
                .sourceType(BookmarkSourceType.FA)
                .build()
        ));
        when(flowAlertRepository.findByAlertIdIn(anyList())).thenReturn(List.of());
        when(unusualWhalesClient.getFlowAlerts(eq(Map.of("limit", 500)))).thenReturn(
            new UnusualWhalesFlowResponse(List.of(upstreamAlert(alertId)), null, null)
        );

        var result = service.getBookmarkedTrades(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tradeId()).isEqualTo(alertId);
        assertThat(result.getFirst().ticker()).isEqualTo("SPY");
        assertThat(result.getFirst().optionChain()).isEqualTo("SPY260418C00500000");
        assertThat(result.getFirst().premium()).isEqualByComparingTo("12300");

        verify(flowAlertRepository).findByAlertIdIn(List.of(alertId));
        verify(unusualWhalesClient).getFlowAlerts(Map.of("limit", 500));
    }

    private UnusualWhalesFlowAlertDto upstreamAlert(UUID alertId) {
        return new UnusualWhalesFlowAlertDto(
            alertId.toString(),
            "SPY",
            "SPY260418C00500000",
            new BigDecimal("500.00"),
            LocalDate.of(2026, 4, 18),
            "call",
            new BigDecimal("1.23"),
            new BigDecimal("1.20"),
            new BigDecimal("1.25"),
            new BigDecimal("500.00"),
            10,
            new BigDecimal("12300"),
            1,
            1000,
            5000,
            new BigDecimal("0.20"),
            new BigDecimal("0.21"),
            new BigDecimal("0.22"),
            "RepeatedHits",
            true,
            false,
            true,
            false,
            false,
            null,
            null,
            null,
            Instant.parse("2026-04-18T14:00:00Z").toEpochMilli(),
            null,
            new BigDecimal("12000"),
            new BigDecimal("300"),
            Instant.parse("2026-04-18T14:00:01Z"),
            null,
            null,
            null,
            null,
            false
        );
    }
}