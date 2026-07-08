package com.marketmono.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.option-trades.aggregation")
@Validated
@Getter
@Setter
public class OptionTradeAggregationProperties {

    private boolean enabled = true;
    private boolean async = true;
    @Min(1)
    private int maxInFlightBatches = 1;
    @Min(0)
    private int queueCapacity = 2;
    @Min(1)
    private int lookupChunkSize = 200;
    @Min(1)
    private int saveChunkSize = 200;
}
