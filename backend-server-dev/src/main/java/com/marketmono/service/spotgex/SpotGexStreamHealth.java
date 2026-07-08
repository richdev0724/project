package com.marketmono.service.spotgex;

import java.util.List;

public record SpotGexStreamHealth(
    String source,
    boolean connected,
    int activeTickerCount,
    List<String> subscribedTickers
) {
}
