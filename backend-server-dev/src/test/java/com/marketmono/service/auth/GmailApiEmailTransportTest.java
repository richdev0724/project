package com.marketmono.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiEmailTransportTest {

    @Test
    void shouldSendPlainTextEmailThroughGmailApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MailDeliveryProperties properties = mailProperties();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(
            () -> "access-token",
            restTemplate,
            properties
        );

        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer access-token"))
            .andExpect(jsonPath("$.raw").isNotEmpty())
            .andRespond(withSuccess("{\"id\":\"gmail-message-id\",\"threadId\":\"gmail-thread-id\"}", MediaType.APPLICATION_JSON));

        transport.sendPlainText("lead@example.com", "Waitlist confirmation", "You are on the waitlist.");

        server.verify();
    }

    @Test
    void shouldBuildMimeMessageWithConfiguredSenderAndBase64UrlRawBody() {
        MailDeliveryProperties properties = mailProperties();
        GmailApiEmailTransport transport = new GmailApiEmailTransport(
            () -> "access-token",
            new RestTemplate(),
            properties
        );

        String raw = transport.buildRawMessage(
            "lead@example.com",
            "Verify your account",
            "<strong>Hello</strong>",
            true
        );

        String mime = new String(Base64.getUrlDecoder().decode(padBase64Url(raw)), StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("+", "/", "=");
        assertThat(mime)
            .contains("From: Market Mono <support@marketmono.com>")
            .contains("To: lead@example.com")
            .contains("Subject: Verify your account")
            .contains("Content-Type: text/html; charset=UTF-8")
            .contains("<strong>Hello</strong>");
    }

    private MailDeliveryProperties mailProperties() {
        MailDeliveryProperties properties = new MailDeliveryProperties();
        properties.setFromEmail("support@marketmono.com");
        properties.setFromName("Market Mono");
        properties.getGmailApi().setApiBaseUrl("https://gmail.googleapis.com/gmail/v1");
        return properties;
    }

    private byte[] padBase64Url(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return (value + "=".repeat(padding)).getBytes(StandardCharsets.UTF_8);
    }
}
