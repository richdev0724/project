package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionChainsResponseDto;
import com.marketmono.service.StrataTickerFamilyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionChainsControllerTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Spy
    private StrataTickerFamilyResolver strataTickerFamilyResolver = new StrataTickerFamilyResolver();

    @InjectMocks
    private OptionChainsController optionChainsController;

    @Test
    void shouldMergeVixFamilyOptionChainsSoVixAndVixwExpiriesAreAvailable() {
        when(unusualWhalesClient.getOptionChains("VIXW", "2026-06-18"))
            .thenReturn(new OptionChainsResponseDto(List.of(
                "VIXW260618C00018000",
                "VIXW260618P00018000"
            )));
        when(unusualWhalesClient.getOptionChains("VIX", "2026-06-18"))
            .thenReturn(new OptionChainsResponseDto(List.of(
                "VIX260717C00020000",
                "VIXW260618C00018000"
            )));

        ResponseEntity<OptionChainsResponseDto> response =
            optionChainsController.getOptionChains("vix", "2026-06-18");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).containsExactly(
            "VIXW260618C00018000",
            "VIXW260618P00018000",
            "VIX260717C00020000"
        );
        verify(unusualWhalesClient).getOptionChains("VIXW", "2026-06-18");
        verify(unusualWhalesClient).getOptionChains("VIX", "2026-06-18");
    }
}
