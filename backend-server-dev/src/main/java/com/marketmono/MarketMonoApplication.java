package com.marketmono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableJpaAuditing
public class MarketMonoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketMonoApplication.class, args);
    }

    @Bean("virtualThreadExecutor")
    Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
