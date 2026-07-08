package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.StockMaxPainResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockV1ControllerTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private StockV1Controller stockV1Controller;

    @Test
    void shouldReturnMaxPainResponseBody() {
        StockMaxPainResponse maxPainResponse = new StockMaxPainResponse(
            List.of(new StockMaxPainResponse.StockMaxPainRow("2026-04-17", "500")),
            "2026-04-17"
        );

        when(unusualWhalesClient.getStockMaxPain("SPY", "2026-04-17")).thenReturn(maxPainResponse);

        ResponseEntity<StockMaxPainResponse> response = stockV1Controller.getStockMaxPain("spy", "2026-04-17");

        assertThat(response.getBody()).isEqualTo(maxPainResponse);
        verify(unusualWhalesClient).getStockMaxPain("SPY", "2026-04-17");
    }
}