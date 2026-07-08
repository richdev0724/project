package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
        "https://accounts.google.com",
        "accounts.google.com"
    );

    private final GoogleOAuthProperties properties;
    private final GooglePublicKeyProvider publicKeyProvider;
    private final ObjectMapper objectMapper;

    public GoogleIdTokenClaims verify(String idToken) {
        if (!StringUtils.hasText(properties.getClientId())) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        if (!StringUtils.hasText(idToken)) {
            throw new IllegalArgumentException("Google credential is required");
        }

        JsonNode header = parseHeader(idToken);
        String algorithm = header.path("alg").asText();
        if (!"RS256".equals(algorithm)) {
            throw new IllegalArgumentException("Google credential uses an unsupported signature algorithm");
        }

        String keyId = header.path("kid").asText();
        PublicKey publicKey = publicKeyProvider.findByKeyId(keyId)
            .orElseThrow(() -> new IllegalArgumentException("Google token signing key is unknown"));

        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(idToken)
                .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Google credential is invalid");
        }

        validateIssuer(claims);
        validateAudience(claims);

        String email = claims.get("email", String.class);
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Google account email is missing");
        }

        boolean emailVerified = booleanClaim(claims.get("email_verified"));
        if (properties.isRequireEmailVerified() && !emailVerified) {
            throw new IllegalArgumentException("Google account email must be verified");
        }

        String hostedDomain = claims.get("hd", String.class);
        validateDomain(email, hostedDomain);

        return new GoogleIdTokenClaims(
            claims.getSubject(),
            email,
            emailVerified,
            claims.get("name", String.class),
            hostedDomain
        );
    }

    private JsonNode parseHeader(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Google credential is invalid");
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Google credential is invalid");
        }
    }

    private void validateIssuer(Claims claims) {
        if (!ALLOWED_ISSUERS.contains(claims.getIssuer())) {
            throw new IllegalArgumentException("Google token issuer is not allowed");
        }
    }

    private void validateAudience(Claims claims) {
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.getClientId())) {
            throw new IllegalArgumentException("Google token audience is not allowed");
        }
    }

    private boolean booleanClaim(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private void validateDomain(String email, String hostedDomain) {
        Set<String> allowedDomains = properties.allowedDomainSet();
        if (allowedDomains.isEmpty()) {
            return;
        }

        String emailDomain = emailDomain(email);
        String normalizedHostedDomain = hostedDomain == null
            ? ""
            : hostedDomain.toLowerCase(Locale.ROOT);

        if (!allowedDomains.contains(emailDomain) && !allowedDomains.contains(normalizedHostedDomain)) {
            throw new IllegalArgumentException("Google account domain is not allowed");
        }
    }

    private String emailDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        return email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }
}
