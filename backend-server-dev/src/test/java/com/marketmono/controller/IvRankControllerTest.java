package com.marketmono.controller;

import com.marketmono.controller.advice.UnusualWhalesShedExceptionHandler;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.IvRankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IvRankControllerTest {

    @Mock
    private IvRankService ivRankService;

    @InjectMocks
    private IvRankController controller;

    @Test
    void shouldReturn503WithRetryAfterWhenMarketDataCallIsShed() throws Exception {
        when(ivRankService.getIvRank("SPY", null, null))
            .thenThrow(new UnusualWhalesShedException("iv rank", "budget", 60));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new UnusualWhalesShedExceptionHandler())
            .build();

        mockMvc.perform(get("/api/v1/iv-rank/SPY"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
            .andExpect(jsonPath("$.code").value("MARKET_DATA_THROTTLED"))
            .andExpect(jsonPath("$.retryAfterSeconds").value(60))
            .andExpect(jsonPath("$.operation").value("iv rank"));
    }
}
