package com.marketmono.service.auth;

public interface EmailTransport {
    void sendHtml(String to, String subject, String htmlContent);

    void sendPlainText(String to, String subject, String textContent);
}
