package com.marketmono.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface FlowFilterDescriptor {
    List<String> tickers();

    List<String> ignoreTickers();

    Instant since();

    Instant until();

    List<LocalDate> expirations();

    LocalDate minExpiryDate();

    LocalDate maxExpiryDate();

    BigDecimal minPremium();

    BigDecimal maxPremium();

    Long minSize();

    Long maxSize();

    Long minVolume();

    Long maxVolume();

    Boolean calls();

    Boolean puts();

    Boolean sweepOnly();

    Boolean floorOnly();

    Boolean hasSweep();

    Boolean hasFloor();

    Boolean hasSingleleg();

    Boolean hasMultileg();

    String side();

    Boolean cross();

    Boolean regular();

    Boolean bid();

    Boolean ask();

    Boolean noSide();

    Boolean aboveAsk();

    Boolean belowBid();

    Boolean volumeGtOi();

    Boolean sizeGtOi();

    Boolean excludeDeepItm();

    Boolean otmOnly();

    Boolean itmOnly();

    Boolean hideExpired();
}
