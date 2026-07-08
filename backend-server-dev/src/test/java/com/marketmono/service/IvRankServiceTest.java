package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.IvRankResponseDto;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.UnusualWhalesShedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IvRankServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Test
    void shouldPropagateShedSignalWithoutWrappingOrCollapsingToEmpty() {
        UnusualWhalesShedException shedException = new UnusualWhalesShedException("iv rank", "budget", 60);
        when(unusualWhalesClient.getIvRank("SPY", null, "6m")).thenThrow(shedException);

        IvRankService service = new IvRankService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getIvRank("SPY", null, "6m"))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldKeepGenuineEmptyResponseAsEmptyList() {
        when(unusualWhalesClient.getIvRank("SPY", null, "6m"))
            .thenReturn(new IvRankResponseDto(List.of()));

        IvRankService service = new IvRankService(unusualWhalesClient);

        assertThat(service.getIvRank("SPY", null, "6m")).isEmpty();
    }
}
