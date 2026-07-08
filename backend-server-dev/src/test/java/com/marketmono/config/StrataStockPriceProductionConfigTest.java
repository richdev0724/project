package com.marketmono.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StrataStockPriceProductionConfigTest {

    @Test
    void prodProfileShouldEnableStrataStockPriceCollectionEveryMinute() throws Exception {
        String prodConfig = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(prodConfig)
            .contains("enabled: ${MARKET_DATA_STRATA_STOCK_PRICES_ENABLED:true}")
            .contains("interval-ms: ${MARKET_DATA_STRATA_STOCK_PRICES_INTERVAL_MS:60000}")
            .contains("initial-delay-ms: ${MARKET_DATA_STRATA_STOCK_PRICES_INITIAL_DELAY_MS:10000}");
    }

    @Test
    void deployWorkflowShouldExportStrataStockPriceCollectionControls() throws Exception {
        String deployWorkflow = Files.readString(Path.of(".github/workflows/deploy-backend.yml"));

        assertThat(deployWorkflow)
            .contains("MARKET_DATA_STRATA_STOCK_PRICES_ENABLED=${{ vars.MARKET_DATA_STRATA_STOCK_PRICES_ENABLED || 'true' }}")
            .contains("MARKET_DATA_STRATA_STOCK_PRICES_INTERVAL_MS=${{ vars.MARKET_DATA_STRATA_STOCK_PRICES_INTERVAL_MS || '60000' }}")
            .contains("MARKET_DATA_STRATA_STOCK_PRICES_INITIAL_DELAY_MS=${{ vars.MARKET_DATA_STRATA_STOCK_PRICES_INITIAL_DELAY_MS || '10000' }}");
    }
}
