package com.marketmono.service.spotgex;

import java.util.function.Consumer;

public interface SpotGexStreamAdapter {

    void subscribe(String ticker);

    void unsubscribe(String ticker);

    void onEvent(Consumer<SpotGexStreamEvent> handler);

    SpotGexStreamHealth health();
}
