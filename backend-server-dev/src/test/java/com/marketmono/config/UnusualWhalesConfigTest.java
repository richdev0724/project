package com.marketmono.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UnusualWhalesConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(TestConfig.class);

    @Test
    void shouldUsePerNodeRateLimitDefaultsForTwoActiveNodes() {
        contextRunner.run(context -> {
            UnusualWhalesConfig config = context.getBean(UnusualWhalesConfig.class);

            assertThat(config.getApi().getMaxConcurrentRequests()).isEqualTo(12);
            assertThat(config.getApi().getRateLimit().getDailyLimit()).isEqualTo(300000);
            assertThat(config.getApi().getRateLimit().getTargetPerMinute()).isEqualTo(490);
            assertThat(config.getApi().getRateLimit().getHardCapPerMinute()).isEqualTo(500);
        });
    }

    @Test
    void applicationConfigUsesCurrentUwAccountLimitDefaults() throws Exception {
        String applicationConfig = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationConfig)
            .contains("max-concurrent-requests: ${UW_API_MAX_CONCURRENT_REQUESTS:12}")
            .contains("daily-limit: ${UW_API_DAILY_LIMIT:300000}")
            .contains("target-per-minute: ${UW_API_TARGET_PER_MINUTE:490}")
            .contains("hard-cap-per-minute: ${UW_API_HARD_CAP_PER_MINUTE:500}");
    }

    @Test
    void deployWorkflowExportsCurrentUwLimitDefaults() throws Exception {
        String deployWorkflow = Files.readString(Path.of(".github/workflows/deploy-backend.yml"));

        assertThat(deployWorkflow)
            .contains("UW_API_MAX_CONCURRENT_REQUESTS=${{ vars.UW_API_MAX_CONCURRENT_REQUESTS || '12' }}")
            .contains("UW_API_DAILY_LIMIT=${{ vars.UW_API_DAILY_LIMIT || '300000' }}")
            .contains("UW_API_TARGET_PER_MINUTE=${{ vars.UW_API_TARGET_PER_MINUTE || '490' }}")
            .contains("UW_API_HARD_CAP_PER_MINUTE=${{ vars.UW_API_HARD_CAP_PER_MINUTE || '500' }}");
    }

    @Test
    void shouldBindBudgetConcurrencyTimeoutAndRetryFields() {
        contextRunner
            .withPropertyValues(
                "unusual-whales.api.base-url=https://example.test",
                "unusual-whales.api.api-key=test-key",
                "unusual-whales.api.max-concurrent-requests=16",
                "unusual-whales.api.rate-limit.daily-limit=12345",
                "unusual-whales.api.rate-limit.target-per-minute=70",
                "unusual-whales.api.rate-limit.hard-cap-per-minute=90",
                "unusual-whales.api.timeout.connect=2500",
                "unusual-whales.api.timeout.read=12000",
                "unusual-whales.api.timeout.permit-wait=750",
                "unusual-whales.api.retry.max-attempts=1",
                "unusual-whales.api.retry.backoff-multiplier=3"
            )
            .run(context -> {
                UnusualWhalesConfig config = context.getBean(UnusualWhalesConfig.class);

                assertThat(config.getApi().getMaxConcurrentRequests()).isEqualTo(16);
                assertThat(config.getApi().getRateLimit().getDailyLimit()).isEqualTo(12345);
                assertThat(config.getApi().getRateLimit().getTargetPerMinute()).isEqualTo(70);
                assertThat(config.getApi().getRateLimit().getHardCapPerMinute()).isEqualTo(90);
                assertThat(config.getApi().getTimeout().getConnect()).isEqualTo(2500);
                assertThat(config.getApi().getTimeout().getRead()).isEqualTo(12000);
                assertThat(config.getApi().getTimeout().getPermitWait()).isEqualTo(750);
                assertThat(config.getApi().getRetry().getMaxAttempts()).isEqualTo(1);
                assertThat(config.getApi().getRetry().getBackoffMultiplier()).isEqualTo(3);
            });
    }

    @Test
    void shouldBindUwCallReportingFields() {
        contextRunner
            .withPropertyValues(
                "unusual-whales.api.reporting.enabled=true",
                "unusual-whales.api.reporting.admin-api-base-url=http://localhost:8081",
                "unusual-whales.api.reporting.internal-secret=test-secret",
                "unusual-whales.api.reporting.connect-timeout=125",
                "unusual-whales.api.reporting.read-timeout=750",
                "unusual-whales.api.reporting.max-attempts=2"
            )
            .run(context -> {
                UnusualWhalesConfig.Reporting reporting = context.getBean(UnusualWhalesConfig.class)
                    .getApi()
                    .getReporting();

                assertThat(reporting.isEnabled()).isTrue();
                assertThat(reporting.getAdminApiBaseUrl()).isEqualTo("http://localhost:8081");
                assertThat(reporting.getInternalSecret()).isEqualTo("test-secret");
                assertThat(reporting.getConnectTimeout()).isEqualTo(125);
                assertThat(reporting.getReadTimeout()).isEqualTo(750);
                assertThat(reporting.getMaxAttempts()).isEqualTo(2);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UnusualWhalesConfig.class)
    static class TestConfig {
    }
}
