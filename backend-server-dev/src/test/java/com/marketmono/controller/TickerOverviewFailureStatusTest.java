package com.marketmono.controller;

import com.marketmono.controller.advice.UnusualWhalesShedExceptionHandler;
import com.marketmono.service.GammaExposureService;
import com.marketmono.service.GreekExposureByStrikeExpiryService;
import com.marketmono.service.IvRankService;
import com.marketmono.service.OptionContractsAllService;
import com.marketmono.service.RealizedVolatilityService;
import com.marketmono.service.StockVolumePriceLevelsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TickerOverviewFailureStatusTest {

    @Test
    void shouldReturn5xxForIvRankUnexpectedFailure() throws Exception {
        IvRankService service = mock(IvRankService.class);
        when(service.getIvRank("SPY", null, null)).thenThrow(new IllegalStateException("boom"));

        mockMvc(new IvRankController(service))
            .perform(get("/api/v1/iv-rank/SPY"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForRealizedVolatilityUnexpectedFailure() throws Exception {
        RealizedVolatilityService service = mock(RealizedVolatilityService.class);
        when(service.getRealizedVolatility("SPY", null, "1Y")).thenThrow(new IllegalStateException("boom"));

        mockMvc(new RealizedVolatilityController(service))
            .perform(get("/api/v1/realized-volatility/SPY"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForGammaExposureUnexpectedFailure() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getGammaExposureByExpiry("SPY", null)).thenThrow(new IllegalStateException("boom"));

        mockMvc(new GammaExposureController(service))
            .perform(get("/api/v1/gamma-exposure/SPY"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForGammaExpirationUnexpectedFailure() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getExpirationDates("SPY")).thenThrow(new IllegalStateException("boom"));

        mockMvc(new GammaExposureController(service))
            .perform(get("/api/v1/gamma-exposure/SPY/expirations"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForGreekExposureByStrikeExpiryUnexpectedFailure() throws Exception {
        GreekExposureByStrikeExpiryService service = mock(GreekExposureByStrikeExpiryService.class);
        when(service.getGreekExposureByStrikeExpiry("SPY", "2026-06-19", null))
            .thenThrow(new IllegalStateException("boom"));

        mockMvc(new GreekExposureByStrikeExpiryController(service))
            .perform(get("/api/v1/greek-exposure-strike-expiry/SPY").param("expiry", "2026-06-19"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForGreekExposureByStrikeUnexpectedFailure() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getGammaExposureByStrike("SPY", null)).thenThrow(new IllegalStateException("boom"));

        mockMvc(new GreekExposureByStrikeController(service))
            .perform(get("/api/v1/greek-exposure-by-strike/SPY"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForOptionContractsUnexpectedFailure() throws Exception {
        OptionContractsAllService service = mock(OptionContractsAllService.class);
        when(service.getOptionContractsAll("SPY", null, null, null, null, null, null, null, null, null, null))
            .thenThrow(new IllegalStateException("boom"));

        mockMvc(new OptionContractsAllController(service))
            .perform(get("/api/v1/option-contracts-all/SPY"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldReturn5xxForVolumePriceLevelsUnexpectedFailure() throws Exception {
        StockVolumePriceLevelsService service = mock(StockVolumePriceLevelsService.class);
        when(service.getVolumePriceLevels("SPY", null)).thenThrow(new IllegalStateException("boom"));

        mockMvc(new StockVolumePriceLevelsController(service))
            .perform(get("/api/v1/volume-price-levels/SPY"))
            .andExpect(status().is5xxServerError());
    }

    private static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new UnusualWhalesShedExceptionHandler())
            .build();
    }
}
