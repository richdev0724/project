package com.marketmono.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GoogleJwkPublicKeyProvider implements GooglePublicKeyProvider {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
    private static final Pattern MAX_AGE_PATTERN = Pattern.compile("max-age=(\\d+)");

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Object cacheLock = new Object();

    private CachedKeys cachedKeys = new CachedKeys(Map.of(), Instant.EPOCH);
    private Instant nextKeyMissRefreshAt = Instant.EPOCH;

    @Autowired
    public GoogleJwkPublicKeyProvider(
        GoogleOAuthProperties properties,
        ObjectMapper objectMapper
    ) {
        this(
            properties,
            objectMapper,
            RestClient.builder()
                .requestFactory(timeoutRequestFactory(properties))
                .build()
        );
    }

    GoogleJwkPublicKeyProvider(
        GoogleOAuthProperties properties,
        ObjectMapper objectMapper,
        RestClient.Builder restClientBuilder
    ) {
        this(properties, objectMapper, restClientBuilder.build());
    }

    GoogleJwkPublicKeyProvider(
        GoogleOAuthProperties properties,
        ObjectMapper objectMapper,
        RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public Optional<PublicKey> findByKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Optional.empty();
        }

        CachedKeys current = currentCachedKeys();
        boolean refreshedThisCall = false;
        if (current.expiresAt().isBefore(Instant.now())) {
            current = refreshKeys(false);
            refreshedThisCall = true;
        }

        if (!refreshedThisCall && !current.keys().containsKey(keyId)) {
            current = refreshKeysAfterKeyMiss();
        }

        return Optional.ofNullable(current.keys().get(keyId));
    }

    private CachedKeys refreshKeysAfterKeyMiss() {
        Instant now = Instant.now();
        synchronized (cacheLock) {
            if (now.isBefore(nextKeyMissRefreshAt)) {
                return cachedKeys;
            }
            nextKeyMissRefreshAt = now.plus(properties.getKeyMissRefreshCooldown());
        }

        return refreshKeys(true);
    }

    private CachedKeys refreshKeys(boolean force) {
        synchronized (cacheLock) {
            CachedKeys current = cachedKeys;
            if (!force && current.expiresAt().isAfter(Instant.now())) {
                return current;
            }

            try {
                ResponseEntity<String> response = restClient.get()
                    .uri(properties.getJwksUri())
                    .retrieve()
                    .toEntity(String.class);

                Map<String, PublicKey> keys = parseKeys(response.getBody());
                Duration ttl = cacheTtl(response.getHeaders());
                CachedKeys refreshed = new CachedKeys(keys, Instant.now().plus(ttl));
                cachedKeys = refreshed;
                return refreshed;
            } catch (RuntimeException ex) {
                log.warn("Failed to refresh Google OAuth signing keys: {}", ex.getMessage());
                return current;
            }
        }
    }

    private CachedKeys currentCachedKeys() {
        synchronized (cacheLock) {
            return cachedKeys;
        }
    }

    private Map<String, PublicKey> parseKeys(String body) {
        try {
            JsonNode keysNode = objectMapper.readTree(body).path("keys");
            Map<String, PublicKey> keys = new HashMap<>();
            for (JsonNode keyNode : keysNode) {
                addRsaPublicKey(keys, keyNode);
            }
            return Map.copyOf(keys);
        } catch (GeneralSecurityException | IllegalArgumentException | java.io.IOException ex) {
            throw new GoogleJwkRefreshException("Failed to parse Google OAuth signing keys", ex);
        }
    }

    private void addRsaPublicKey(Map<String, PublicKey> keys, JsonNode keyNode) throws GeneralSecurityException {
        if (!"RSA".equals(keyNode.path("kty").asText())) {
            return;
        }

        String keyId = keyNode.path("kid").asText();
        String modulus = keyNode.path("n").asText();
        String exponent = keyNode.path("e").asText();
        if (!keyId.isBlank() && !modulus.isBlank() && !exponent.isBlank()) {
            keys.put(keyId, rsaPublicKey(modulus, exponent));
        }
    }

    private PublicKey rsaPublicKey(String modulus, String exponent) throws GeneralSecurityException {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger n = new BigInteger(1, decoder.decode(modulus));
        BigInteger e = new BigInteger(1, decoder.decode(exponent));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private Duration cacheTtl(HttpHeaders headers) {
        String cacheControl = headers.getCacheControl();
        if (cacheControl != null) {
            Matcher matcher = MAX_AGE_PATTERN.matcher(cacheControl);
            if (matcher.find()) {
                long seconds = Long.parseLong(matcher.group(1));
                if (seconds > 0) {
                    return Duration.ofSeconds(seconds);
                }
            }
        }
        return DEFAULT_CACHE_TTL;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory(GoogleOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getJwksConnectTimeout());
        requestFactory.setReadTimeout(properties.getJwksReadTimeout());
        return requestFactory;
    }

    private record CachedKeys(Map<String, PublicKey> keys, Instant expiresAt) {
    }

    private static final class GoogleJwkRefreshException extends RuntimeException {
        private GoogleJwkRefreshException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
