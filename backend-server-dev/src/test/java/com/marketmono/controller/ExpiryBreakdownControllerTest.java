package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.ExpiryBreakdownDto;
import com.marketmono.dto.ExpiryBreakdownResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiryBreakdownControllerTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @InjectMocks
    private ExpiryBreakdownController expiryBreakdownController;

    @Test
    void shouldReturnExpiryBreakdownForRequestedTicker() {
        ExpiryBreakdownResponseDto upstreamResponse = new ExpiryBreakdownResponseDto(List.of(
            new ExpiryBreakdownDto(LocalDate.of(2026, 7, 17), 274_967L, 2_131_188L, 1_186)
        ));
        when(unusualWhalesClient.getExpiryBreakdown("SPXW")).thenReturn(upstreamResponse);

        ResponseEntity<ExpiryBreakdownResponseDto> response =
            expiryBreakdownController.getExpiryBreakdown("spxw");

        assertThat(response.getBody()).isSameAs(upstreamResponse);
        verify(unusualWhalesClient).getExpiryBreakdown("SPXW");
    }
}
