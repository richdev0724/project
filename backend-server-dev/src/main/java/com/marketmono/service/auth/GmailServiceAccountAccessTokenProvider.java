package com.marketmono.service.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class GmailServiceAccountAccessTokenProvider implements GmailAccessTokenProvider {
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String JWT_BEARER_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final long TOKEN_EXPIRY_SKEW_SECONDS = 60;

    private final RestTemplate restTemplate;
    private final MailDeliveryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CachedToken cachedToken;

    GmailServiceAccountAccessTokenProvider(RestTemplate restTemplate, MailDeliveryProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public synchronized String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.validAt(Instant.now())) {
            return current.accessToken();
        }

        try {
            ServiceAccountKey key = readServiceAccountKey();
            String tokenUri = tokenUri(key);
            String assertion = createAssertion(key, tokenUri);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", JWT_BEARER_GRANT);
            body.add("assertion", assertion);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            JsonNode response = restTemplate.postForObject(
                tokenUri,
                new HttpEntity<>(body, headers),
                JsonNode.class
            );

            if (response == null || response.path("access_token").asText("").isBlank()) {
                throw new IllegalStateException("Google OAuth token response did not include an access token");
            }

            long expiresIn = response.path("expires_in").asLong(3600);
            CachedToken token = new CachedToken(
                response.path("access_token").asText(),
                Instant.now().plusSeconds(expiresIn)
            );
            cachedToken = token;
            return token.accessToken();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Gmail API access token: {}", e.getMessage());
            throw new IllegalStateException("Failed to create Gmail API access token", e);
        }
    }

    private ServiceAccountKey readServiceAccountKey() throws IOException {
        JsonNode json = objectMapper.readTree(serviceAccountJson());
        ServiceAccountKey key = new ServiceAccountKey(
            json.path("type").asText(),
            json.path("client_email").asText(),
            json.path("private_key").asText(),
            json.path("private_key_id").asText(),
            json.path("token_uri").asText()
        );

        if (!"service_account".equals(key.type())) {
            throw new IllegalStateException("Gmail API credentials must be a service_account key");
        }
        if (key.clientEmail().isBlank() || key.privateKey().isBlank()) {
            throw new IllegalStateException("Gmail API service account key is missing client_email or private_key");
        }
        return key;
    }

    private String serviceAccountJson() throws IOException {
        MailDeliveryProperties.GmailApi gmail = properties.getGmailApi();
        if (hasText(gmail.getServiceAccountJsonBase64())) {
            return new String(
                Base64.getDecoder().decode(gmail.getServiceAccountJsonBase64().trim()),
                StandardCharsets.UTF_8
            );
        }
        if (hasText(gmail.getServiceAccountJson())) {
            return gmail.getServiceAccountJson();
        }
        if (hasText(gmail.getServiceAccountJsonPath())) {
            return Files.readString(Path.of(gmail.getServiceAccountJsonPath().trim()), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Gmail API service account credentials are not configured");
    }

    private String createAssertion(ServiceAccountKey key, String tokenUri) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            if (hasText(key.privateKeyId())) {
                header.put("kid", key.privateKeyId());
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", key.clientEmail());
            payload.put("scope", GMAIL_SEND_SCOPE);
            payload.put("aud", tokenUri);
            payload.put("iat", now);
            payload.put("exp", now + 3600);
            payload.put("sub", delegatedUser());

            String unsigned = base64Url(objectMapper.writeValueAsBytes(header))
                + "."
                + base64Url(objectMapper.writeValueAsBytes(payload));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey(key.privateKey()));
            signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
            return unsigned + "." + base64Url(signature.sign());
        } catch (GeneralSecurityException | JsonProcessingException | IllegalArgumentException e) {
            throw new GmailAccessTokenException("Failed to create Gmail API JWT assertion", e);
        }
    }

    private PrivateKey privateKey(String privateKeyPem) throws GeneralSecurityException {
        String encoded = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String delegatedUser() {
        String configured = properties.getGmailApi().getDelegatedUser();
        if (hasText(configured)) {
            return configured.trim();
        }
        String fromEmail = properties.getFromEmail();
        if (hasText(fromEmail)) {
            return fromEmail.trim();
        }
        throw new IllegalStateException("Gmail API delegated user is not configured");
    }

    private String tokenUri(ServiceAccountKey key) {
        String configured = properties.getGmailApi().getTokenUri();
        if (hasText(configured)) {
            return configured.trim();
        }
        if (hasText(key.tokenUri())) {
            return key.tokenUri().trim();
        }
        return "https://oauth2.googleapis.com/token";
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ServiceAccountKey(
        String type,
        String clientEmail,
        String privateKey,
        String privateKeyId,
        String tokenUri
    ) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        private boolean validAt(Instant now) {
            return expiresAt.minusSeconds(TOKEN_EXPIRY_SKEW_SECONDS).isAfter(now);
        }
    }

    private static final class GmailAccessTokenException extends RuntimeException {
        private GmailAccessTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
