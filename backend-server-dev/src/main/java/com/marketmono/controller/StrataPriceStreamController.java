package com.marketmono.controller;

import com.marketmono.dto.CrmPriceStreamSnapshotDto;
import com.marketmono.service.CrmPriceStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP entry point for CRM live prices on the Strata page.
 *
 * <p>Clients subscribe to {@code /app/strata-prices/{ticker}} and receive a
 * one-shot snapshot telling them whether the ticker is tracked by the CRM
 * price stream (plus the last known tick). Tracked tickers then stream live
 * updates on {@code /topic/strata-prices/{ticker}}; untracked tickers should
 * fall back to the existing REST pipeline.
 */
@Controller
@RequiredArgsConstructor
public class StrataPriceStreamController {

    private final CrmPriceStreamService crmPriceStreamService;

    @SubscribeMapping("/strata-prices/{ticker}")
    public CrmPriceStreamSnapshotDto onSubscribe(@DestinationVariable String ticker) {
        return crmPriceStreamService.getSnapshot(ticker);
    }
}
