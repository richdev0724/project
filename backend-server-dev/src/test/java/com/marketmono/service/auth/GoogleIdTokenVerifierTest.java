package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    @Test
    void shouldVerifySignedGoogleIdToken() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GoogleIdTokenVerifier verifier = verifierFor(
            "marketmono-client.apps.googleusercontent.com",
            keyPair.getPublic()
        );

        GoogleIdTokenClaims claims = verifier.verify(signedToken(
            keyPair,
            "marketmono-client.apps.googleusercontent.com",
            true,
            "person@example.com",
            "Google Person",
            null
        ));

        assertThat(claims.subject()).isEqualTo("google-subject");
        assertThat(claims.email()).isEqualTo("person@example.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.name()).isEqualTo("Google Person");
    }

    @Test
    void shouldRejectGoogleIdTokenForUnexpectedAudience() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GoogleIdTokenVerifier verifier = verifierFor(
            "marketmono-client.apps.googleusercontent.com",
            keyPair.getPublic()
        );

        String token = signedToken(
            keyPair,
            "other-client.apps.googleusercontent.com",
            true,
            "person@example.com",
            "Google Person",
            null
        );

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Google token audience is not allowed");
    }

    @Test
    void shouldRejectGoogleIdTokenOutsideAllowedDomain() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setClientId("marketmono-client.apps.googleusercontent.com");
        properties.setAllowedDomains("marketmono.com");
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier(
            properties,
            keyId -> Optional.of(keyPair.getPublic()),
            new ObjectMapper()
        );

        String token = signedToken(
            keyPair,
            "marketmono-client.apps.googleusercontent.com",
            true,
            "person@example.com",
            "Google Person",
            null
        );

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Google account domain is not allowed");
    }

    private static GoogleIdTokenVerifier verifierFor(String clientId, PublicKey publicKey) {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setClientId(clientId);
        return new GoogleIdTokenVerifier(
            properties,
            keyId -> Optional.of(publicKey),
            new ObjectMapper()
        );
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String signedToken(
        KeyPair keyPair,
        String audience,
        boolean emailVerified,
        String email,
        String name,
        String hostedDomain
    ) {
        Date now = new Date();
        var builder = Jwts.builder()
            .header()
                .keyId("test-key")
                .and()
            .subject("google-subject")
            .issuer("https://accounts.google.com")
            .audience()
                .add(audience)
                .and()
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 300_000))
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .claim("name", name);

        if (hostedDomain != null) {
            builder.claim("hd", hostedDomain);
        }

        return builder
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }
}
