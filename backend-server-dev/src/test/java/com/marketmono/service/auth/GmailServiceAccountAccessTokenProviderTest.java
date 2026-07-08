package com.marketmono.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailServiceAccountAccessTokenProviderTest {

    @Test
    void shouldRequestDelegatedGmailSendTokenFromServiceAccountJsonBase64() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MailDeliveryProperties properties = new MailDeliveryProperties();
        properties.setFromEmail("support@marketmono.com");
        properties.getGmailApi().setServiceAccountJsonBase64(serviceAccountJsonBase64());
        properties.getGmailApi().setDelegatedUser("support@marketmono.com");
        properties.getGmailApi().setTokenUri("https://oauth2.googleapis.test/token");

        server.expect(requestTo("https://oauth2.googleapis.test/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
            )))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("assertion=")))
            .andRespond(withSuccess("{\"access_token\":\"delegated-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        GmailServiceAccountAccessTokenProvider provider = new GmailServiceAccountAccessTokenProvider(
            restTemplate,
            properties
        );

        assertThat(provider.getAccessToken()).isEqualTo("delegated-token");
        server.verify();
    }

    @Test
    void shouldReuseCachedAccessTokenWhileStillValid() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MailDeliveryProperties properties = new MailDeliveryProperties();
        properties.setFromEmail("support@marketmono.com");
        properties.getGmailApi().setServiceAccountJsonBase64(serviceAccountJsonBase64());
        properties.getGmailApi().setDelegatedUser("support@marketmono.com");
        properties.getGmailApi().setTokenUri("https://oauth2.googleapis.test/token");

        server.expect(requestTo("https://oauth2.googleapis.test/token"))
            .andRespond(withSuccess("{\"access_token\":\"cached-token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        GmailServiceAccountAccessTokenProvider provider = new GmailServiceAccountAccessTokenProvider(
            restTemplate,
            properties
        );

        assertThat(provider.getAccessToken()).isEqualTo("cached-token");
        assertThat(provider.getAccessToken()).isEqualTo("cached-token");
        server.verify();
    }

    private String serviceAccountJsonBase64() throws Exception {
        String privateKeyPem = privateKeyPem();
        String json = """
            {
              "type": "service_account",
              "project_id": "market-mono-email",
              "private_key_id": "test-key-id",
              "private_key": "%s",
              "client_email": "marketmono-email-sender@market-mono-email.iam.gserviceaccount.com",
              "client_id": "103626719369814700478",
              "token_uri": "https://oauth2.googleapis.test/token"
            }
            """.formatted(privateKeyPem.replace("\n", "\\n"));
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String privateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PrivateKey privateKey = generator.generateKeyPair().getPrivate();
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
    }
}
