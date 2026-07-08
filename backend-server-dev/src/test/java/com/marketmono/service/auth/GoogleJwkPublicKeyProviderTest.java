package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleJwkPublicKeyProviderTest {

    @Test
    void shouldRefreshKeysWhenRequestedKeyIdIsMissingFromFreshCache() throws Exception {
        KeyPair firstKey = generateRsaKeyPair();
        KeyPair secondKey = generateRsaKeyPair();
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setJwksUri("https://google.test/oauth2/v3/certs");

        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleJwkPublicKeyProvider provider = new GoogleJwkPublicKeyProvider(
            properties,
            new ObjectMapper(),
            restClientBuilder
        );

        server.expect(requestTo("https://google.test/oauth2/v3/certs"))
            .andRespond(withSuccess(jwks("first-key", firstKey), MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));

        assertThat(provider.findByKeyId("first-key")).isPresent();
        server.verify();
        server.reset();

        server.expect(requestTo("https://google.test/oauth2/v3/certs"))
            .andRespond(withSuccess(jwks("second-key", secondKey), MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));

        assertThat(provider.findByKeyId("second-key")).isPresent();
        server.verify();
    }

    @Test
    void shouldThrottleRepeatedUnknownKeyRefreshesWhileCacheIsFresh() throws Exception {
        KeyPair firstKey = generateRsaKeyPair();
        KeyPair replacementKey = generateRsaKeyPair();
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setJwksUri("https://google.test/oauth2/v3/certs");

        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleJwkPublicKeyProvider provider = new GoogleJwkPublicKeyProvider(
            properties,
            new ObjectMapper(),
            restClientBuilder
        );

        server.expect(requestTo("https://google.test/oauth2/v3/certs"))
            .andRespond(withSuccess(jwks("first-key", firstKey), MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));

        assertThat(provider.findByKeyId("first-key")).isPresent();
        server.verify();
        server.reset();

        server.expect(requestTo("https://google.test/oauth2/v3/certs"))
            .andRespond(withSuccess(jwks("replacement-key", replacementKey), MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));

        assertThat(provider.findByKeyId("attacker-random-key-1")).isEmpty();
        server.verify();
        server.reset();

        assertThat(provider.findByKeyId("attacker-random-key-2")).isEmpty();
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenJwksFetchFails() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setJwksUri("https://google.test/oauth2/v3/certs");

        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleJwkPublicKeyProvider provider = new GoogleJwkPublicKeyProvider(
            properties,
            new ObjectMapper(),
            restClientBuilder
        );

        server.expect(requestTo("https://google.test/oauth2/v3/certs"))
            .andRespond(withException(new SocketTimeoutException("timed out")));

        assertThat(provider.findByKeyId("google-key")).isEmpty();
        server.verify();
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(String keyId, KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return """
            {
              "keys": [
                {
                  "kty": "RSA",
                  "kid": "%s",
                  "use": "sig",
                  "alg": "RS256",
                  "n": "%s",
                  "e": "%s"
                }
              ]
            }
            """.formatted(
                keyId,
                base64Url(publicKey.getModulus()),
                base64Url(publicKey.getPublicExponent())
            );
    }

    private static String base64Url(BigInteger value) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray());
    }
}
