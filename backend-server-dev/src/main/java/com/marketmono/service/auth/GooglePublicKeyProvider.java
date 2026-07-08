package com.marketmono.service.auth;

import java.security.PublicKey;
import java.util.Optional;

@FunctionalInterface
public interface GooglePublicKeyProvider {
    Optional<PublicKey> findByKeyId(String keyId);
}
