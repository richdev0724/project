package com.marketmono.service;

import com.marketmono.config.WhopWebhookProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WhopWebhookVerifierSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(WhopWebhookProperties.class, WhopWebhookVerifier.class)
        .withPropertyValues("whop.webhook.secret=test-secret");

    @Test
    void createsVerifierBeanWithTestOnlyClockConstructorPresent() {
        contextRunner.run(context ->
            assertThat(context).hasSingleBean(WhopWebhookVerifier.class)
        );
    }
}
