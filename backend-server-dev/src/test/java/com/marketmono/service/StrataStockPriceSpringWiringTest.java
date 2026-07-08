package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.config.StrataStockPriceCollectionProperties;
import com.marketmono.repository.StrataStockPriceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StrataStockPriceSpringWiringTest {

    @Test
    void shouldWireStrataStockPriceServicesWithSpringConstructorInjection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(UnusualWhalesClient.class, () -> mock(UnusualWhalesClient.class));
            context.registerBean(StrataStockPriceTickerUniverseService.class,
                () -> mock(StrataStockPriceTickerUniverseService.class));
            context.registerBean(StrataStockPriceRepository.class, () -> mock(StrataStockPriceRepository.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            context.registerBean(StrataStockPriceCollectionProperties.class,
                StrataStockPriceCollectionProperties::new);
            context.register(StrataStockPriceCollectionService.class, StrataStockPriceLatestService.class);

            context.refresh();

            assertThat(context.getBean(StrataStockPriceCollectionService.class)).isNotNull();
            assertThat(context.getBean(StrataStockPriceLatestService.class)).isNotNull();
        }
    }
}
