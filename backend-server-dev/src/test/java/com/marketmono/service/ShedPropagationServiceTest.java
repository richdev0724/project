package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.exception.MarketDataProviderException;
import com.marketmono.exception.OhlcRequestException;
import com.marketmono.exception.UnusualWhalesShedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShedPropagationServiceTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Test
    void shouldPropagateRealizedVolatilityShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("realized volatility");
        when(unusualWhalesClient.getRealizedVolatility("SPY", null, "1Y")).thenThrow(shedException);

        RealizedVolatilityService service = new RealizedVolatilityService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getRealizedVolatility("SPY", null, "1Y"))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateGammaExposureByExpiryShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("greek exposure by expiry");
        when(unusualWhalesClient.getGreekExposureByExpiry("SPY", null)).thenThrow(shedException);

        GammaExposureService service = new GammaExposureService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getGammaExposureByExpiry("SPY", null))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateGammaExposureByStrikeShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("greek exposure by strike");
        when(unusualWhalesClient.getGreekExposureByStrike("SPY", null)).thenThrow(shedException);

        GammaExposureService service = new GammaExposureService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getGammaExposureByStrike("SPY", null))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateGreekExposureByStrikeExpiryShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("greek exposure by strike expiry");
        when(unusualWhalesClient.getGreekExposureByStrikeExpiry("SPY", "2026-06-19", null))
            .thenThrow(shedException);

        GreekExposureByStrikeExpiryService service = new GreekExposureByStrikeExpiryService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getGreekExposureByStrikeExpiry("SPY", "2026-06-19", null))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateInsiderTransactionsShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("insider transactions");
        when(unusualWhalesClient.getInsiderTransactions("SPY", 10)).thenThrow(shedException);

        InsiderTransactionsService service = new InsiderTransactionsService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getInsiderTransactions("SPY", 10))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateVolumePriceLevelsShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("volume price levels");
        when(unusualWhalesClient.getStockVolumePriceLevels("SPY", null)).thenThrow(shedException);

        StockVolumePriceLevelsService service = new StockVolumePriceLevelsService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getVolumePriceLevels("SPY", null))
            .isSameAs(shedException)
            .isNotInstanceOf(MarketDataProviderException.class);
    }

    @Test
    void shouldPropagateOhlcShedSignalWithoutWrapping() {
        UnusualWhalesShedException shedException = shedException("OHLC data");
        when(unusualWhalesClient.getOhlcData("SPY", "1d", null, null, null, null))
            .thenThrow(shedException);

        OhlcService service = new OhlcService(unusualWhalesClient);

        assertThatThrownBy(() -> service.getOhlcData("SPY", "1d", null, null, null, null))
            .isSameAs(shedException)
            .isNotInstanceOf(OhlcRequestException.class);
    }

    private static UnusualWhalesShedException shedException(String operation) {
        return new UnusualWhalesShedException(operation, "budget", 60);
    }
}
