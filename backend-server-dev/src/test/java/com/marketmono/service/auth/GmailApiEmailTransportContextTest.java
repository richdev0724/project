package com.marketmono.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GmailApiEmailTransportContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(MailDeliveryProperties.class)
        .withBean(GmailApiEmailTransport.class)
        .withPropertyValues(
            "app.mail.provider=gmail-api",
            "app.mail.from-email=support@marketmono.com",
            "app.mail.from-name=Market Mono"
        );

    @Test
    void shouldCreateGmailApiTransportInSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GmailApiEmailTransport.class);
            assertThat(context).hasSingleBean(EmailTransport.class);
        });
    }
}
