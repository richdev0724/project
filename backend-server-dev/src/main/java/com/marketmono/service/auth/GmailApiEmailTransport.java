package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "gmail-api")
@Slf4j
public class GmailApiEmailTransport implements EmailTransport {
    private final GmailAccessTokenProvider accessTokenProvider;
    private final RestTemplate restTemplate;
    private final MailDeliveryProperties properties;

    @Autowired
    public GmailApiEmailTransport(MailDeliveryProperties properties) {
        this(
            new GmailServiceAccountAccessTokenProvider(new RestTemplate(), properties),
            new RestTemplate(),
            properties
        );
    }

    GmailApiEmailTransport(
        GmailAccessTokenProvider accessTokenProvider,
        RestTemplate restTemplate,
        MailDeliveryProperties properties
    ) {
        this.accessTokenProvider = accessTokenProvider;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public void sendHtml(String to, String subject, String htmlContent) {
        send(to, subject, htmlContent, true);
    }

    @Override
    public void sendPlainText(String to, String subject, String textContent) {
        send(to, subject, textContent, false);
    }

    private void send(String to, String subject, String content, boolean html) {
        String raw = buildRawMessage(to, subject, content, html);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenProvider.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("raw", raw);
        String url = baseUrl() + "/users/me/messages/send";
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
        log.debug("Sent email through Gmail API to {}", to);
    }

    String buildRawMessage(String to, String subject, String content, boolean html) {
        String mime = String.join("\r\n",
            "From: " + fromAddress(),
            "To: " + sanitizeHeader(to),
            "Subject: " + sanitizeHeader(subject),
            "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()),
            "MIME-Version: 1.0",
            "Content-Type: " + (html ? "text/html" : "text/plain") + "; charset=UTF-8",
            "Content-Transfer-Encoding: 8bit",
            "",
            content
        );
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    private String fromAddress() {
        String fromEmail = properties.getFromEmail();
        String fromName = properties.getFromName();
        if (fromName == null || fromName.isBlank()) {
            return sanitizeHeader(fromEmail);
        }

        return sanitizeHeader(fromName) + " <" + sanitizeHeader(fromEmail) + ">";
    }

    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String baseUrl() {
        String value = properties.getGmailApi().getApiBaseUrl();
        if (value == null || value.isBlank()) {
            return "https://gmail.googleapis.com/gmail/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
