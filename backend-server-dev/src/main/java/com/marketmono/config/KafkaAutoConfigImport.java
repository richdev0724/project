package com.marketmono.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

/**
 * Conditionally re-imports KafkaAutoConfiguration only when spring.kafka.enabled=true.
 *
 * By default, KafkaAutoConfiguration is excluded in {@link com.marketmono.MarketMonoApplication}
 * to prevent the Kafka client from attempting to connect/authenticate when no password is set.
 * This class re-enables it when Kafka is explicitly turned on.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Import(KafkaAutoConfiguration.class)
@Lazy(false)
public class KafkaAutoConfigImport {
}
