package com.marketmono.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "noop")
@Slf4j
public class NoopEmailTransport implements EmailTransport {

    @Override
    public void sendHtml(String to, String subject, String htmlContent) {
        log.info("Skipping HTML email delivery through noop transport to: {}", to);
    }

    @Override
    public void sendPlainText(String to, String subject, String textContent) {
        log.info("Skipping plain text email delivery through noop transport to: {}", to);
    }
}
