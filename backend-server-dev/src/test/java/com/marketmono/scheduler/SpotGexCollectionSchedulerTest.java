package com.marketmono.scheduler;

import com.marketmono.config.SpotGexCollectionProperties;
import com.marketmono.service.MarketHoursService;
import com.marketmono.service.SpotGexCollectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotGexCollectionSchedulerTest {

    @Mock
    private MarketHoursService marketHoursService;

    @Mock
    private SpotGexCollectionService spotGexCollectionService;

    @Mock
    private SpotGexCollectionProperties properties;

    @InjectMocks
    private SpotGexCollectionScheduler scheduler;

    @Test
    void shouldRunCollectionWhenMarketIsOpen() {
        when(marketHoursService.isRegularMarketOpen()).thenReturn(true);

        scheduler.collectSpotGexData();

        verify(spotGexCollectionService).collectAll();
    }

    @Test
    void shouldSkipCollectionWhenMarketIsClosed() {
        when(marketHoursService.isRegularMarketOpen()).thenReturn(false);

        scheduler.collectSpotGexData();

        verify(spotGexCollectionService, never()).collectAll();
    }

    @Test
    void shouldSkipCollectionWhenStreamSourceIsActiveAndMarketIsClosed() {
        when(marketHoursService.isRegularMarketOpen()).thenReturn(false);

        scheduler.collectSpotGexData();

        verify(spotGexCollectionService, never()).collectAll();
        verify(spotGexCollectionService, never()).collectRestOnlyTickers();
    }

    @Test
    void shouldCollectRestOnlyTickersWhenStreamSourceIsActiveAndMarketIsOpen() {
        when(properties.getSource()).thenReturn(SpotGexCollectionProperties.SourceMode.STREAM);
        when(marketHoursService.isRegularMarketOpen()).thenReturn(true);

        scheduler.collectSpotGexData();

        verify(spotGexCollectionService, never()).collectAll();
        verify(spotGexCollectionService).collectRestOnlyTickers();
    }
}
