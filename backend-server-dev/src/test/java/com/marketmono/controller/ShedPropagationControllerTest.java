package com.marketmono.controller;

import com.marketmono.controller.advice.UnusualWhalesShedExceptionHandler;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.GammaExposureService;
import com.marketmono.service.GreekExposureByStrikeExpiryService;
import com.marketmono.service.InsiderTransactionsService;
import com.marketmono.service.OhlcService;
import com.marketmono.service.OptionContractsAllService;
import com.marketmono.service.RealizedVolatilityService;
import com.marketmono.service.StockVolumePriceLevelsService;
import com.marketmono.service.TickerOptionsVolumeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShedPropagationControllerTest {

    @Test
    void shouldReturn503ForRealizedVolatilityShedSignal() throws Exception {
        RealizedVolatilityService service = mock(RealizedVolatilityService.class);
        when(service.getRealizedVolatility("SPY", null, "1Y")).thenThrow(shedException("realized volatility"));

        MockMvc mockMvc = mockMvc(new RealizedVolatilityController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/realized-volatility/SPY")), "realized volatility");
    }

    @Test
    void shouldReturn503ForGammaExpirationShedSignal() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getExpirationDates("SPY")).thenThrow(shedException("greek exposure by expiry"));

        MockMvc mockMvc = mockMvc(new GammaExposureController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/gamma-exposure/SPY/expirations")), "greek exposure by expiry");
    }

    @Test
    void shouldReturn503ForGammaExposureShedSignal() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getGammaExposureByExpiry("SPY", null)).thenThrow(shedException("greek exposure by expiry"));

        MockMvc mockMvc = mockMvc(new GammaExposureController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/gamma-exposure/SPY")), "greek exposure by expiry");
    }

    @Test
    void shouldReturn503ForInsiderTransactionsShedSignal() throws Exception {
        InsiderTransactionsService service = mock(InsiderTransactionsService.class);
        when(service.getInsiderTransactions("SPY", 10)).thenThrow(shedException("insider transactions"));

        MockMvc mockMvc = mockMvc(new InsiderTransactionsController(service));

        assertShedResponse(
            mockMvc.perform(get("/api/v1/insider-trades").param("ticker_symbol", "spy").param("limit", "10")),
            "insider transactions"
        );
    }

    @Test
    void shouldReturn503ForOptionContractsShedSignal() throws Exception {
        OptionContractsAllService service = mock(OptionContractsAllService.class);
        when(service.getOptionContractsAll("SPY", null, null, null, null, null, null, null, null, null, null))
            .thenThrow(shedException("all option contracts"));

        MockMvc mockMvc = mockMvc(new OptionContractsAllController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/option-contracts-all/SPY")), "all option contracts");
    }

    @Test
    void shouldReturn503ForGreekExposureByStrikeExpiryShedSignal() throws Exception {
        GreekExposureByStrikeExpiryService service = mock(GreekExposureByStrikeExpiryService.class);
        when(service.getGreekExposureByStrikeExpiry("SPY", "2026-06-19", null))
            .thenThrow(shedException("greek exposure by strike expiry"));

        MockMvc mockMvc = mockMvc(new GreekExposureByStrikeExpiryController(service));

        assertShedResponse(
            mockMvc.perform(get("/api/v1/greek-exposure-strike-expiry/SPY").param("expiry", "2026-06-19")),
            "greek exposure by strike expiry"
        );
    }

    @Test
    void shouldReturn503ForGreekExposureByStrikeShedSignal() throws Exception {
        GammaExposureService service = mock(GammaExposureService.class);
        when(service.getGammaExposureByStrike("SPY", null)).thenThrow(shedException("greek exposure by strike"));

        MockMvc mockMvc = mockMvc(new GreekExposureByStrikeController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/greek-exposure-by-strike/SPY")), "greek exposure by strike");
    }

    @Test
    void shouldReturn503ForOptionsVolumeShedSignal() throws Exception {
        TickerOptionsVolumeService service = mock(TickerOptionsVolumeService.class);
        when(service.getOptionsVolume("SPY", 10)).thenThrow(shedException("options volume"));

        MockMvc mockMvc = mockMvc(new OptionsVolumeController(service));

        assertShedResponse(
            mockMvc.perform(get("/api/v1/ticker-options-volume").param("ticker", "spy").param("limit", "10")),
            "options volume"
        );
    }

    @Test
    void shouldReturn503ForVolumePriceLevelsShedSignal() throws Exception {
        StockVolumePriceLevelsService service = mock(StockVolumePriceLevelsService.class);
        when(service.getVolumePriceLevels("SPY", null)).thenThrow(shedException("volume price levels"));

        MockMvc mockMvc = mockMvc(new StockVolumePriceLevelsController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/volume-price-levels/SPY")), "volume price levels");
    }

    @Test
    void shouldReturn503ForOhlcShedSignal() throws Exception {
        OhlcService service = mock(OhlcService.class);
        when(service.getOhlcData("SPY", "1d", null, null, null, null)).thenThrow(shedException("OHLC data"));

        MockMvc mockMvc = mockMvc(new OhlcController(service));

        assertShedResponse(mockMvc.perform(get("/api/v1/ohlc/spy/1d")), "OHLC data");
    }

    private static MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new UnusualWhalesShedExceptionHandler())
            .build();
    }

    private static UnusualWhalesShedException shedException(String operation) {
        return new UnusualWhalesShedException(operation, "budget", 60);
    }

    private static void assertShedResponse(
            org.springframework.test.web.servlet.ResultActions response,
            String operation
    ) throws Exception {
        response.andExpect(status().isServiceUnavailable())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
            .andExpect(jsonPath("$.code").value("MARKET_DATA_THROTTLED"))
            .andExpect(jsonPath("$.retryAfterSeconds").value(60))
            .andExpect(jsonPath("$.operation").value(operation));
    }
}
